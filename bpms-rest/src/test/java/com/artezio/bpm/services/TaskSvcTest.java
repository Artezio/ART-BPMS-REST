package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.formio.client.FormClient;
import junitx.framework.ListAssert;
import junitx.util.PrivateAccessor;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertNotNull;
import static org.junit.Assert.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class TaskSvcTest extends ServiceTest {

    @InjectMocks
    private TaskSvc taskSvc = new TaskSvc();
    @Mock
    private IdentitySvc identityService;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private RepositoryService repositoryService;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private FormService formService;
    @Mock
    private FormSvc formSvc;
    @Mock
    private FormClient formio;
    @Mock
    private VariablesMapper variablesMapper;
    @Mock
    private VariableValidator variableValidator;

    @Before
    public void init() throws NoSuchFieldException {
        Field taskServiceField = taskSvc.getClass().getDeclaredField("taskService");
        Field runtimeServiceField = taskSvc.getClass().getDeclaredField("runtimeService");
        setField(taskSvc, taskServiceField, getTaskService());
        setField(taskSvc, runtimeServiceField, getRuntimeService());
    }

    @After
    public void tearDown() {
        TaskService taskService = getTaskService();
        Set<String> taskIds = taskService.createTaskQuery()
                .list()
                .stream()
                .map(Task::getId)
                .collect(Collectors.toSet());
        getHistoryService().createHistoricTaskInstanceQuery()
                .finished()
                .list()
                .forEach(historicTaskInstance -> taskIds.add(historicTaskInstance.getId()));

        taskIds.forEach(taskId -> getHistoryService().deleteHistoricTaskInstance(taskId));
        taskIds.stream()
                .map(taskId -> getTaskService().createTaskQuery().taskId(taskId).singleResult())
                .filter(Objects::nonNull)
                .map(Task::getProcessDefinitionId)
                .filter(Objects::nonNull)
                .forEach(processDefinitionId -> getRepositoryService().deleteProcessDefinition(processDefinitionId, true));
        taskService.deleteTasks(taskIds);
    }

    @Test
    public void testListAvailable_ByCandidateUser() {
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");
        createTask("testTask1", null, callerId, emptyList());
        createTask("testTask2", null, callerId, emptyList());

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable();

        assertFalse(actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateUser_NoAvailableTasks() {
        String candidateId = "candidateId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");

        createTask("testTask1", null, candidateId, emptyList());
        createTask("testTask2", null, candidateId, emptyList());

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateGroup() {
        String candidateId = "candidateId";
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");

        createTask("testTask1", null, candidateId, callerGroups);
        createTask("testTask2", null, candidateId, callerGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable();

        assertTrue(!actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateGroup_NoAvailableTasks() {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");

        createTask("testTask1", null, candidateId, candidateGroups);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testListAssigned() {
        String callerId = "callerId";

        createTask("testTask", callerId, callerId, emptyList());

        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> taskRepresentationList = taskSvc.listAssigned();

        assertTrue(!taskRepresentationList.isEmpty());
    }

    @Test
    public void testListAssigned_NoAssignedTasks() {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String callerId = "callerId";

        createTask("testTask", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> taskRepresentationList = taskSvc.listAssigned();

        assertTrue(taskRepresentationList.isEmpty());
    }

    @Test
    public void testClaim_CallerHasAccess() {
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");
        String taskId = "testTask";

        createTask(taskId, callerId, callerId, callerGroups);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);

        taskSvc.claim(taskId);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testClaim_CallerHasNoAccess() {
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String taskId = "testTask";

        createTask(taskId, candidateId, candidateId, candidateGroups);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);

        taskSvc.claim(taskId);
    }

    @Test
    public void testLoadForm() {
        String taskId = "taskId";
        String callerId = "callerId";
        String taskFormDefinition = "taskFormDefinition";
        List<String> callerGroups = asList("callerGroup");
        String formKey = "formKey";
        String expected = "form";
        Map<String, Object> taskVariables = new HashMap<>();

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getTaskFormWithData(taskId, taskVariables)).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
    }

    @Test
    public void testLoadForm_VariableHasMultipleFiles() throws IOException {
        String taskId = "taskId";
        String callerId = "callerId";
        String taskFormDefinition = "taskFormDefinition";
        List<String> callerGroups = asList("callerGroup");
        String fileVariableName = "testFile";
        String fileName1 = "testFile.png";
        String fileName2 = "testForm.json";
        File file1 = getFile(fileName1);
        File file2 = getFile(fileName2);
        Map<String, Object> fileValue1 = getFileAsAttributesMap(file1);
        Map<String, Object> fileValue2 = getFileAsAttributesMap(file2);
        String formKey = "formKey";
        String expected = "{ \"title\": \"form\", " +
                    "[" +
                        getFileValueRepresentationJson(fileValue1) + "," +
                        getFileValueRepresentationJson(fileValue2) +
                    "]" +
                "}";
        List<Map<String, Object>> fileValues = asList(fileValue1, fileValue2);
        Map<String, Object> taskVariables = new HashMap<String, Object>() {{
            put(fileVariableName, fileValues);
        }};

        Map<String, Object> fileValueRepresentation1 = getFileAsAttributesMap(file1);
        Map<String, Object> fileValueRepresentation2 = getFileAsAttributesMap(file2);
        List<Map<String, Object>> fileValueRepresentations = asList(fileValueRepresentation1, fileValueRepresentation2);
        ArgumentCaptor<Map<String, Object>> convertedTaskVariablesCaptor = ArgumentCaptor.forClass(Map.class);

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(formService.getTaskFormData(taskId).getFormKey()).thenReturn(formKey);
        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getTaskFormWithData(eq(taskId), convertedTaskVariablesCaptor.capture())).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
        ListAssert.assertEquals(fileValueRepresentations, (List) convertedTaskVariablesCaptor.getValue().get(fileVariableName));
    }

    @Test
    public void testLoadForm_VariableDoesntExist_ExecutionVariableIsContainer_ContainerHasSimpleVariables() {
        String taskId = "taskId";
        String callerId = "callerId";
        String formKey = "formKey";
        String expected = "{ \"title\": \"form\", { \"container1\": { \"container2\": { \"var\": \"val\" } } } }";
        List<String> callerGroups = asList("callerGroup");
        Map<String, Object> taskVariablesRepresentation = new HashMap<String, Object>() {{
            put("container1", new HashMap<String, Object>() {{
                put("container2", new HashMap<String, Object>() {{
                    put("var", "val");
                }});
            }});
        }};
        Map<String, Object> container1 = new HashMap<>();
        Map<String, Object> container2 = new HashMap<>();
        container1.put("container2", container2);
        container2.put("var", "val");
        Map<String, Object> taskVariables = new HashMap<>();
        taskVariables.put("container1", container1);

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(formService.getTaskFormData(taskId).getFormKey()).thenReturn(formKey);
        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getTaskFormWithData(taskId, taskVariablesRepresentation)).thenReturn(expected);
        when(formSvc.getTaskFormWithData(taskId, taskVariables)).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
    }

    @Test
    public void testLoadForm_VariableDoesntExist_ExecutionVariableIsContainer_ContainerHasFileVariables() throws IOException {
        String taskId = "taskId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");
        String formKey = "formKey";
        String taskFormDefinition = "taskFormDefinition";
        String container1VariableName = "container1";
        String container2VariableName = "container2";
        String fileVariableName = "testFile";
        String fileName = "testFile.png";
        String filePath = "/bpms-rest/api/task/" + taskId + "/file?filePath=" +
                container1VariableName + "/" +
                container2VariableName + "/" +
                fileVariableName + "[?(@.originalName == '" + fileName + "')]";
        File file = getFile(fileName);
        Map<String, Object> fileValue = getFileAsAttributesMap(file);
        Map<String, Object> convertedFileValue = getFileAsAttributesMap(file);
        String expected = "taskFormJsonWithoutData";
        ArgumentCaptor<Map<String, Object>> convertedTaskVariablesCaptor = ArgumentCaptor.forClass(Map.class);

        Map<String, Object> taskVariables = new HashMap<>();
        Map<String, Object> container1 = new HashMap<>();
        Map<String, Object> container2 = new HashMap<>();
        taskVariables.put(container1VariableName, container1);
        container1.put(container2VariableName, container2);
        container2.put(fileVariableName, asList(fileValue));

        Map<String, Object> convertedTaskVariables = new HashMap<>();
        Map<String, Object> convertedContainer1Variable = new HashMap<>();
        Map<String, Object> convertedContainer2Variable = new HashMap<>();
        convertedTaskVariables.put(container1VariableName,  convertedContainer1Variable);
        convertedContainer1Variable.put(container2VariableName, convertedContainer2Variable);
        List<Map<String, Object>> convertedFileValues = asList(convertedFileValue);
        convertedContainer2Variable.put(fileVariableName, convertedFileValues);

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(formService.getTaskFormData(taskId).getFormKey()).thenReturn(formKey);
        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getTaskFormWithData(eq(taskId), convertedTaskVariablesCaptor.capture())).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
        List<Map<String, Object>> capturedFileValues = ((Map<String, Map<String, List>>) convertedTaskVariablesCaptor.getValue()
                .get("container1"))
                .get("container2")
                .get(fileVariableName);
        ListAssert.assertEquals(convertedFileValues, capturedFileValues);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testLoadForm_CallerHasNoAccess() {
        String taskId = "taskId";
        String callerId = "callerId";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        List<String> callerGroups = asList("callerGroup");
        createTask(taskId, candidateUserId, candidateUserId, candidateGroups);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);

        taskSvc.loadForm(taskId);
    }

    @Ignore
    @Test
    public void testComplete_DecisionHasSubmittedValue() throws IOException, NoSuchFieldException {
        String callerId = "callerId";
        String taskId = "testTask";
        String formPath = "formPath";
        String cleanDataJson = "{\"var1\": \"value1\"}";
        String decision = "submitted";
        Map<String, Object> inputVariables = new HashMap<String, Object>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> expectedVariables = new HashMap<String, Object>() {{
            put("var1", "value1");
            put("decision", decision);
        }};
        ArgumentCaptor<Map<String, Object>> taskVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        Map<String, Object> taskVariables = new HashMap<String, Object>() {{put("var1", "");}};

        createTask(taskId, callerId, callerId, emptyList());
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(formSvc.shouldSkipValidation(taskId, decision)).thenReturn(false);
        when(formSvc.dryValidationAndCleanupTaskForm(taskId, inputVariables)).thenReturn(cleanDataJson);
        when(formService
                .getTaskFormData(taskId)
                .getFormKey())
                .thenReturn(formPath);

        RuntimeService mockRuntimeService = mock(RuntimeService.class);
        ProcessInstanceQuery mockProcessInstanceQuery = mock(ProcessInstanceQuery.class);
        PrivateAccessor.setField(taskSvc, "runtimeService", mockRuntimeService);
        when(identityService.userId()).thenReturn("user");
        ProcessInstance mockInstance = mock(ProcessInstance.class);
        when(mockRuntimeService.createProcessInstanceQuery()).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.processInstanceId(any())).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.singleResult()).thenReturn(mockInstance);
        when(mockInstance.getId()).thenReturn("id1");
        when(identityService.userId()).thenReturn(callerId);

        doAnswer(invocationOnMock -> {
            taskVariablesCaptor.getValue().clear();
            taskVariablesCaptor.getValue().putAll(expectedVariables);
            return null;
        }).when(variablesMapper).updateVariables(taskVariablesCaptor.capture(), eq(cleanDataJson));

        taskSvc.complete(taskId, inputVariables);

        HistoricTaskInstance historicTaskInstance = getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        assertEquals(taskId, historicTaskInstance.getId());
        assertEquals("completed", historicTaskInstance.getDeleteReason());
        assertEquals(expectedVariables, taskVariablesCaptor.getValue());
    }

    @Ignore
    @Test
    public void testComplete_DecisionHasRejectedValue() throws IOException, NoSuchFieldException {
        String callerId = "callerId";
        String taskId = "testTask";
        String decision = "rejected";
        Map<String, Object> inputVariables = new HashMap<String, Object>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> taskVariables = new HashMap<>();

        createTask(taskId, callerId, callerId, emptyList());
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(formSvc.shouldSkipValidation(taskId, decision)).thenReturn(true);
        RuntimeService mockRuntimeService = mock(RuntimeService.class);
        ProcessInstanceQuery mockProcessInstanceQuery = mock(ProcessInstanceQuery.class);
        PrivateAccessor.setField(taskSvc, "runtimeService", mockRuntimeService);
        when(identityService.userId()).thenReturn("user");
        ProcessInstance mockInstance = mock(ProcessInstance.class);
        when(mockRuntimeService.createProcessInstanceQuery()).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.processInstanceId(any())).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.singleResult()).thenReturn(mockInstance);
        when(mockInstance.getId()).thenReturn("id1");
        when(identityService.userId()).thenReturn(callerId);

        taskSvc.complete(taskId, inputVariables);

        HistoricTaskInstance historicTaskInstance = getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        assertEquals(taskId, historicTaskInstance.getId());
        assertEquals("completed", historicTaskInstance.getDeleteReason());

        verify(formService, never()).getTaskFormData(taskId);
    }

    @Ignore
    @Test
    public void testComplete_FormKeyIsNull() throws IOException, NoSuchFieldException {
        String callerId = "callerId";
        String taskId = "testTask";
        String formKey = null;
        String decision = "submitted";
        String processDefinitionKey = "processDefinitionKey";
        Map<String, Object> inputVariables = new HashMap<String, Object>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> expectedVariables = new HashMap<String, Object>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        BpmnModelInstance bpmnModelInstance = mock(BpmnModelInstance.class);
        Process processElement = mock(Process.class);

        createTask(taskId, callerId, callerId, emptyList());
        setVariablesToTask(taskId, expectedVariables);

        RuntimeService mockRuntimeService = mock(RuntimeService.class);
        ProcessInstanceQuery mockProcessInstanceQuery = mock(ProcessInstanceQuery.class);
        PrivateAccessor.setField(taskSvc, "runtimeService", mockRuntimeService);
        when(identityService.userId()).thenReturn("user");
        ProcessInstance mockInstance = mock(ProcessInstance.class);
        when(mockRuntimeService.createProcessInstanceQuery()).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.processInstanceId(any())).thenReturn(mockProcessInstanceQuery);
        when(mockProcessInstanceQuery.singleResult()).thenReturn(mockInstance);
        when(mockInstance.getId()).thenReturn("id1");
        when(identityService.userId()).thenReturn(callerId);
        when(formService.getTaskFormData(taskId).getFormKey()).thenReturn(formKey);
        when(formSvc.shouldSkipValidation(taskId, decision)).thenReturn(false);
        when(repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(nullable(String.class))
                .singleResult()
                .getKey())
                .thenReturn(processDefinitionKey);
        when(repositoryService.getBpmnModelInstance(anyString())).thenReturn(bpmnModelInstance);
        when(bpmnModelInstance.getModelElementById(processDefinitionKey)).thenReturn(processElement);

        taskSvc.complete(taskId, inputVariables);

        Map<String, Object> actualVariables = getVariablesFromHistoryService(taskId);

        assertEquals(actualVariables, expectedVariables);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testComplete_TaskIsNotAssignedToCaller() throws IOException {
        String candidateUserId = "candidateUserId";
        String callerId = "callerId";
        String taskId = "testTask";
        Map<String, Object> inputVariables = new HashMap<>();

        createTask(taskId, candidateUserId, candidateUserId, emptyList());
        setVariablesToTask(taskId, inputVariables);

        when(identityService.userId()).thenReturn(callerId);

        taskSvc.complete(taskId, inputVariables);
    }

    @Test
    public void testDownloadFile() throws IOException {
        String taskId = "taskId";
        String fileName = "testFile.png";
        String fileVariableName = "testVar";
        String filePath = fileVariableName + "[*]/[?(@.originalName == '" + fileName + "')]";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
//        Map<String, Object> fileValue = getFileAsAttributesMap(getFile(fileName));
        FileValue fileValue = getFileValue(getFile(fileName));
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(fileVariableName, asList(fileValue));
        }};
        String mimeType = "image/png";
        Response expected = Response.ok(IOUtils.toByteArray(fileValue.getValue()), MediaType.valueOf(mimeType))
                .header("Content-Disposition", "attachment; filename=" + fileName)
                .build();

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        Response actual = taskSvc.downloadFile(taskId, filePath);

        assertFileResponseEquals(expected, actual);
    }

    @Test
    public void testDownloadFile_FileIsInContainer() throws IOException {
        String taskId = "taskId";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        String fileName = "testFile.png";
        String containerVariableName = "container";
        String fileVariableName = "testVar";
        String filePath = containerVariableName + "/" + fileVariableName + "[*]/[?(@.originalName == '" + fileName + "')]";
        Map<String, Object> containerVariable = new HashMap<>();
        Map<String, Object> fileValue = getFileAsAttributesMap(getFile(fileName));
        containerVariable.put(fileVariableName, asList(fileValue));
        Map<String, Object> variables = new HashMap<>();
        variables.put(containerVariableName, containerVariable);

        Response expected = Response
                .ok(getFileContentFromUrl((String) fileValue.get("url")), MediaType.valueOf("image/png"))
                .header("Content-Disposition", "attachment; filename=" + fileName)
                .build();

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        Response actual = taskSvc.downloadFile(taskId, filePath);

        assertFileResponseEquals(expected, actual);
    }

    @Test
    public void testDownloadFile_FileHasEmptyMimeType() throws IOException {
        String taskId = "taskId";
        String fileName = "testFile.png";
        String fileVariableName = "testVar";
        String filePath = fileVariableName + "[*]/[?(@.originalName == '" + fileName + "')]";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        Map<String, Object> fileValue = getFileAsAttributesMap(getFile(fileName));
        fileValue.put("type", "");
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(fileVariableName, asList(fileValue));
        }};
        Response expected = Response.ok(getFileContentFromUrl((String) fileValue.get("url")), MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "attachment; filename=" + fileName)
                .build();

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        Response actual = taskSvc.downloadFile(taskId, filePath);

        assertFileResponseEquals(expected, actual);
    }

    @Test(expected = NotFoundException.class)
    public void testDownloadFile_FileDoesntExist() {
        String taskId = "taskId";
        String filePath = "testVar/otherFile.txt";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        String filename = "file.txt";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(filePath, asList(Variables.fileValue(filename).file(new byte[]{}).create()));
        }};

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        taskSvc.downloadFile(taskId, filePath);
    }

    @Test(expected = NotFoundException.class)
    public void testDownloadFile_IncorrectFilePathFormat() {
        String taskId = "taskId";
        String filePath = "testVar\\otherFile.txt";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        String filename = "file.txt";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(filePath, asList(Variables.fileValue(filename).file(new byte[]{}).create()));
        }};

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        taskSvc.downloadFile(taskId, filePath);
    }

    @Test(expected = NotFoundException.class)
    public void testDownloadFile_ThereIsNoFileName() {
        String taskId = "taskId";
        String filePath = "testVar/";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        String filename = "file.txt";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(filePath, asList(Variables.fileValue(filename).file(new byte[]{}).create()));
        }};

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);

        taskSvc.downloadFile(taskId, filePath);
    }

    @Test
    public void testGetNextAssignedTask() throws IOException {
        when(identityService.userId()).thenReturn("testUser");
        Deployment deployment = createDeployment("tasks", "process-with-assigned-task.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_with_assigned_task");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNotNull(nextAssignedTask);
        assertEquals("Task 1", nextAssignedTask.getName());
    }

    @Test
    public void testGetNextAssignedTask_multipleTasks() throws IOException {
        when(identityService.userId()).thenReturn("testUser");
        Deployment deployment = createDeployment("tasks", "process-with-two-simultaneous-assigned-tasks.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_with_two_simultaneous_assigned_tasks");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNull(nextAssignedTask);
    }

    @Test
    public void testGetNextAssignedTask_noTasks() throws IOException {
        when(identityService.userId()).thenReturn("testUser");
        Deployment deployment = createDeployment("tasks", "test-process-startable-by-testUser.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("testProcessStartableByTestUser");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNull(nextAssignedTask);
    }

}
