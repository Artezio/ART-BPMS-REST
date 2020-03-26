package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.rest.query.task.TaskQueryParams;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.forms.FileStorage;
import junitx.framework.ListAssert;
import junitx.util.PrivateAccessor;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.runtime.ProcessInstanceQuery;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
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
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.util.AnnotationLiteral;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static junit.framework.TestCase.assertNotNull;
import static org.camunda.bpm.engine.test.assertions.ProcessEngineTests.*;
import static org.junit.Assert.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;
import static org.powermock.reflect.Whitebox.setInternalState;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CDI.class})
@PowerMockIgnore({"com.sun.org.apache.*", "javax.xml.*", "java.xml.*", "org.xml.*", "org.w3c.dom.*"})
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
    private VariableValidator variableValidator;
    @Mock
    private VariablesMapper variablesMapper;
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
    @Mock
    private FileStorage fileStorage;

    @Before
    public void init() {
        setupMockFileStorage();
        setInternalState(taskSvc, "taskService", getTaskService());
        setInternalState(taskSvc, "fileStorage", fileStorage);
    }

    private void setupMockFileStorage() {
        PowerMockito.mockStatic(CDI.class);
        CDI mockCdi = mock(CDI.class);
        Instance mockFileStorageInstance = mock(Instance.class);
        Instance mockConcreteFileStorageInstance = mock(Instance.class);
        when(CDI.current()).thenReturn(mockCdi);
        when(mockCdi.select(FileStorage.class)).thenReturn(mockFileStorageInstance);
        when(mockFileStorageInstance.select(any(AnnotationLiteral.class))).thenReturn(mockConcreteFileStorageInstance);
        when(mockConcreteFileStorageInstance.get()).thenReturn(fileStorage);
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
        TaskQueryParams queryParams = new TaskQueryParams();

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertFalse(actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateUser_NoAvailableTasks() {
        String candidateId = "candidateId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");
        TaskQueryParams queryParams = new TaskQueryParams();

        createTask("testTask1", null, candidateId, emptyList());
        createTask("testTask2", null, candidateId, emptyList());

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateGroup() {
        String candidateId = "candidateId";
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");
        TaskQueryParams queryParams = new TaskQueryParams();

        createTask("testTask1", null, candidateId, callerGroups);
        createTask("testTask2", null, candidateId, callerGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertTrue(!actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByCandidateGroup_NoAvailableTasks() {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("testGroup");
        TaskQueryParams queryParams = new TaskQueryParams();

        createTask("testTask1", null, candidateId, candidateGroups);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testListAvailable_ByDueDate() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueDate(dueDate);

        createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
    }

    @Test
    public void testListAvailable_ByDueDateExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        String dueDateExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueDateExpression(dueDateExpression);

        createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
    }

    @Test
    public void testListAvailable_ByDueAfter() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDueDate = "2019-01-01 00:00:00.000+0000";
        String stringRepresentationOfDueDateAfter = "2018-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueAfter(dateFormatter.parse(stringRepresentationOfDueDateAfter));

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByDueAfterExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDueDate = "2019-01-01 00:00:00.000+0000";
        String dueAfterExpression = "${dateTime().parse(\"2018-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueAfterExpression(dueAfterExpression);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        Task testTask2 = createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByDueBefore() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDueDate = "2018-01-01 00:00:00.000+0000";
        String stringRepresentationOfDueDateBefore = "2019-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueBefore(dateFormatter.parse(stringRepresentationOfDueDateBefore));

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByDueBeforeExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDueDate = "2018-01-01 00:00:00.000+0000";
        String dueBeforeExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueBeforeExpression(dueBeforeExpression);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, dueDate, null);
        createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpDate() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpDate(followUpDate);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpDateExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        String followUpDateExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpDateExpression(followUpDateExpression);

        createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
    }

    @Test
    public void testListAvailable_ByFollowUpAfter() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfDueDate = "2019-01-01 00:00:00.000+0000";
        String stringRepresentationOfFollowUpAfterDate = "2018-01-01 00:00:00.000+0000";
        Date followUpAfterDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpAfter(dateFormatter.parse(stringRepresentationOfFollowUpAfterDate));

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpAfterDate);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpAfterExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfFollowUpAfterDate = "2019-01-01 00:00:00.000+0000";
        String followUpAfterExpression = "${dateTime().parse(\"2018-01-01T00:00:00.000+0000\")}";
        Date followUpAfterDate = dateFormatter.parse(stringRepresentationOfFollowUpAfterDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpAfterExpression(followUpAfterExpression);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpAfterDate);
        Task testTask2 = createTask("testTask2", null, candidateId, candidateGroups, null, null);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpBefore() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfFollowUpDate = "2018-01-01 00:00:00.000+0000";
        String stringRepresentationOfFollowUpBeforeDate = "2019-01-01 00:00:00.000+0000";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfFollowUpDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpBefore(dateFormatter.parse(stringRepresentationOfFollowUpBeforeDate));

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpBeforeExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfFollowUpDate = "2018-01-01 00:00:00.000+0000";
        String followUpBeforeExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfFollowUpDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpBeforeExpression(followUpBeforeExpression);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpBeforeExpressionOrNotExistent() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfFollowUpDate = "2018-01-01 00:00:00.000+0000";
        String stringRepresentationOfFollowUpBeforeDate = "2019-01-01 00:00:00.000+0000";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfFollowUpDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpBeforeOrNotExistent(dateFormatter.parse(stringRepresentationOfFollowUpBeforeDate));

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        Task testTask2 = createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(2, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
        assertEquals(testTask2.getId(), actual.get(1).getId());
    }

    @Test
    public void testListAvailable_ByFollowUpBeforeExpressionOrNotExistentExpression() throws ParseException {
        String candidateId = "candidateId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        List<String> callerGroups = asList("candidateGroup");
        String stringRepresentationOfFollowUpDate = "2018-01-01 00:00:00.000+0000";
        String followUpBeforeExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date followUpDate = dateFormatter.parse(stringRepresentationOfFollowUpDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setFollowUpBeforeOrNotExistentExpression(followUpBeforeExpression);

        Task testTask1 = createTask("testTask1", null, candidateId, candidateGroups, null, followUpDate);
        Task testTask2 = createTask("testTask2", null, candidateId, candidateGroups);

        when(identityService.userGroups()).thenReturn(callerGroups);
        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> actual = taskSvc.listAvailable(queryParams);

        assertEquals(2, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
        assertEquals(testTask2.getId(), actual.get(1).getId());
    }

    @Test
    public void testListAssigned() {
        String callerId = "callerId";
        TaskQueryParams queryParams = new TaskQueryParams();

        createTask("testTask", callerId, callerId, emptyList());

        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> taskRepresentationList = taskSvc.listAssigned(queryParams);

        assertTrue(!taskRepresentationList.isEmpty());
    }

    @Test
    public void testListAssigned_NoAssignedTasks() {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String callerId = "callerId";
        TaskQueryParams queryParams = new TaskQueryParams();

        createTask("testTask", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(callerId);

        List<TaskRepresentation> taskRepresentationList = taskSvc.listAssigned(queryParams);

        assertTrue(taskRepresentationList.isEmpty());
    }

    @Test
    public void testListAssigned_ByDueDate() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueDate(dateFormatter.parse(stringRepresentationOfDate));

        Task testTask1 = createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAssigned_ByDueDateExpression() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDate = "2019-01-01 00:00:00.000+0000";
        String dueDateExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueDateExpression(dueDateExpression);

        createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
    }

    @Test
    public void testListAssigned_ByDueAfter() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDueDate = "2019-01-01 00:00:00.000+0000";
        String stringRepresentationOfDueAfterDate = "2018-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueAfter(dateFormatter.parse(stringRepresentationOfDueAfterDate));

        Task testTask1 = createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAssigned_ByDueAfterExpression() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDueDate = "2019-01-01 00:00:00.000+0000";
        String dueAfterExpression = "${dateTime().parse(\"2018-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueAfterExpression(dueAfterExpression);

        Task testTask1 = createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAssigned_ByDueBefore() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDueDate = "2018-01-01 00:00:00.000+0000";
        String stringRepresentationOfDueBeforeDate = "2019-01-01 00:00:00.000+0000";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueBefore(dateFormatter.parse(stringRepresentationOfDueBeforeDate));

        Task testTask1 = createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
    }

    @Test
    public void testListAssigned_ByDueBeforeExpression() throws ParseException {
        String candidateId = "candidateId";
        String taskAssignee = "taskAssignee";
        String stringRepresentationOfDueDate = "2018-01-01 00:00:00.000+0000";
        String dueBeforeExpression = "${dateTime().parse(\"2019-01-01T00:00:00.000+0000\")}";
        Date dueDate = dateFormatter.parse(stringRepresentationOfDueDate);
        TaskQueryParams queryParams = new TaskQueryParams();
        queryParams.setDueBeforeExpression(dueBeforeExpression);

        Task testTask1 = createTask("testTask1", taskAssignee, candidateId, emptyList(), dueDate, null);
        createTask("testTask2", taskAssignee, candidateId, emptyList());

        when(identityService.userId()).thenReturn(taskAssignee);

        List<TaskRepresentation> actual = taskSvc.listAssigned(queryParams);

        assertEquals(1, actual.size());
        assertEquals(testTask1.getId(), actual.get(0).getId());
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
    public void testLoadForm() throws IOException {
        String taskId = "taskId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");
        String expected = "form";
        Map<String, Object> taskVariables = new HashMap<>();

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getTaskFormWithData(taskId, taskVariables, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
    }

    @Test
    public void testLoadForm_VariableHasMultipleFiles() throws IOException, URISyntaxException {
        String taskId = "taskId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");
        String fileVariableName = "testFile";
        String fileName1 = "testFile.png";
        String fileName2 = "testForm.json";
        File file1 = getFile(fileName1);
        File file2 = getFile(fileName2);
        Map<String, Object> fileValue1 = getFileAsAttributesMap(file1);
        Map<String, Object> fileValue2 = getFileAsAttributesMap(file2);
        String expected = "{ \"title\": \"form\", " +
                    "[" +
                        getFileValueRepresentationJson(fileValue1) + "," +
                        getFileValueRepresentationJson(fileValue2) +
                    "]" +
                "}";
        List<Map<String, Object>> fileValues = asList(fileValue1, fileValue2);
        Map<String, Object> taskVariables = new HashMap<>() {{
            put(fileVariableName, fileValues);
        }};

        Map<String, Object> fileValueRepresentation1 = getFileAsAttributesMap(file1);
        Map<String, Object> fileValueRepresentation2 = getFileAsAttributesMap(file2);
        List<Map<String, Object>> fileValueRepresentations = asList(fileValueRepresentation1, fileValueRepresentation2);
        ArgumentCaptor<Map<String, Object>> convertedTaskVariablesCaptor = ArgumentCaptor.forClass(Map.class);

        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(asList(fileVariableName));
        when(formSvc.getTaskFormWithData(eq(taskId), convertedTaskVariablesCaptor.capture(), eq(PUBLIC_RESOURCES_DIRECTORY))).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
        ListAssert.assertEquals(fileValueRepresentations, (List) convertedTaskVariablesCaptor.getValue().get(fileVariableName));
    }

    @Test
    public void testLoadForm_VariableDoesntExist_ExecutionVariableIsContainer_ContainerHasSimpleVariables() throws IOException {
        String taskId = "taskId";
        String callerId = "callerId";
        String expected = "{ \"title\": \"form\", { \"container1\": { \"container2\": { \"var\": \"val\" } } } }";
        List<String> callerGroups = asList("callerGroup");
        Map<String, Object> container1 = new HashMap<>();
        Map<String, Object> container2 = new HashMap<>();
        container1.put("container2", container2);
        container2.put("var", "val");
        Map<String, Object> taskVariables = new HashMap<>();
        taskVariables.put("container1", container1);
        createTask(taskId, callerId, callerId, callerGroups);
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(asList("container1"));
        when(formSvc.getTaskFormWithData(taskId, taskVariables, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
    }

    @Test
    public void testLoadForm_VariableDoesntExist_ExecutionVariableIsContainer_ContainerHasFileVariables() throws IOException, URISyntaxException {
        String taskId = "taskId";
        String callerId = "callerId";
        List<String> callerGroups = asList("callerGroup");
        String container1VariableName = "container1";
        String container2VariableName = "container2";
        String fileVariableName = "testFile";
        String fileName = "testFile.png";
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

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(callerGroups);
        when(formSvc.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(asList(container1VariableName));
        when(formSvc.getTaskFormWithData(eq(taskId), convertedTaskVariablesCaptor.capture(), eq(PUBLIC_RESOURCES_DIRECTORY))).thenReturn(expected);

        String actual = taskSvc.loadForm(taskId);

        assertEquals(expected, actual);
        List<Map<String, Object>> capturedFileValues = ((Map<String, Map<String, List>>) convertedTaskVariablesCaptor.getValue()
                .get("container1"))
                .get("container2")
                .get(fileVariableName);
        ListAssert.assertEquals(convertedFileValues, capturedFileValues);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testLoadForm_CallerHasNoAccess() throws IOException {
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

    @Test
    @Ignore
    public void testComplete_DecisionHasSubmittedValue() throws Exception {
        String callerId = "callerId";
        String taskId = "testTask";
        String formPath = "formPath";
        String cleanDataJson = "{\"var1\": \"value1\"}";
        String decision = "submitted";
        Map<String, Object> inputVariables = new HashMap<>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> expectedVariables = new HashMap<>() {{
            put("var1", "value1");
            put("decision", decision);
        }};
        ArgumentCaptor<Map<String, Object>> taskVariablesCaptor = ArgumentCaptor.forClass(Map.class);
        Map<String, Object> taskVariables = new HashMap<>() {{
            put("var1", "");
        }};

        Task task = createTask(taskId, callerId, callerId, emptyList());
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(formSvc.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(false);
//        when(formSvc.dryValidationAndCleanupTaskForm(taskId, inputVariables, taskVariables, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(cleanDataJson);
        when(formService
                .getTaskFormData(taskId)
                .getFormKey())
                .thenReturn(formPath);

        RuntimeService mockRuntimeService = mock(RuntimeService.class);
        ProcessInstanceQuery mockProcessInstanceQuery = mock(ProcessInstanceQuery.class);
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
        doNothing().when(variableValidator).validate(expectedVariables);

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
        Map<String, Object> inputVariables = new HashMap<>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> taskVariables = new HashMap<>();

        createTask(taskId, callerId, callerId, emptyList());
        setVariablesToTask(taskId, taskVariables);

        when(identityService.userId()).thenReturn(callerId);
        when(formSvc.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(true);
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
        Map<String, Object> inputVariables = new HashMap<>() {{
            put("var1", "value1");
            put("var2", "value2");
            put("decision", decision);
        }};
        Map<String, Object> expectedVariables = new HashMap<>() {{
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
        when(formSvc.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(false);
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

    @Test(expected = com.artezio.bpm.services.exceptions.NotAuthorizedException.class)
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
    public void testDownloadFile() throws IOException, URISyntaxException {
        String taskId = "taskId";
        String fileName = "testFile.png";
        List<String> formVariableNames = asList("var1", "var1.var11", "var1.var12", "var2", "var2.var21", "var2.var21.file211");
        String fileId = "var2.var21[0].file211[0]";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        File file = getFile(fileName);
        FileValue fileValue = getFileValue(file);
        Map<String, Object> variables = new HashMap<>() {{
            put(fileId, fileValue);
        }};
        String mimeType = "image/png";
        InputStream in = new FileInputStream(file);
        Response expected = Response.ok(in, MediaType.valueOf(mimeType))
                .header("Content-Disposition", "attachment; filename=" + fileName)
                .build();

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);
        when(formSvc.getTaskFormFieldPaths(taskId, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(formVariableNames);

        Response actual = taskSvc.downloadFile(taskId, fileId);

        assertFileResponseEquals(expected, actual);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testDownloadFile_UserHasNoAccess() {
        String taskId = "taskId";
        String fileId = "var2.var21.var211.file2111[0]";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        Map<String, Object> variables = new HashMap<>();

        createTask(taskId, "", candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(asList(""));

        taskSvc.downloadFile(taskId, fileId);
    }

    @Test(expected = NotFoundException.class)
    public void testDownloadFile_UserHasNoAccessToTask() {
        String taskId = "taskId";
        List<String> formVariableNames = asList("var1", "var11", "var2", "var21", "file211");
        String fileId = "var2.var21.var211.file2111[0]";
        String candidateUserId = "candidateUserId";
        List<String> candidateGroups = asList("candidateGroup");
        String callerId = "callerId";
        Map<String, Object> variables = new HashMap<>();

        createTask(taskId, callerId, candidateUserId, candidateGroups);
        setVariablesToTask(taskId, variables);

        when(identityService.userId()).thenReturn(callerId);
        when(identityService.userGroups()).thenReturn(candidateGroups);
        when(formSvc.getTaskFormFieldPaths(taskId, PUBLIC_RESOURCES_DIRECTORY)).thenReturn(formVariableNames);

        taskSvc.downloadFile(taskId, fileId);
    }

    @Test
    public void testGetNextAssignedTask() throws IOException, URISyntaxException {
        when(identityService.userId()).thenReturn("testUser");
        createDeployment("tasks", "process-with-assigned-task.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_with_assigned_task");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNotNull(nextAssignedTask);
        assertEquals("Task 1", nextAssignedTask.getName());
    }

    @Test
    public void testGetNextAssignedTask_multipleTasks() throws IOException, URISyntaxException {
        when(identityService.userId()).thenReturn("testUser");
        createDeployment("tasks", "process-with-two-simultaneous-assigned-tasks.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_with_two_simultaneous_assigned_tasks");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNull(nextAssignedTask);
    }

    @Test
    public void testGetNextAssignedTask_noTasks() throws IOException, URISyntaxException {
        when(identityService.userId()).thenReturn("testUser");
        createDeployment("tasks", "test-process-startable-by-testUser.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("testProcessStartableByTestUser");

        Task nextAssignedTask = taskSvc.getNextAssignedTask(processInstance.getProcessInstanceId());

        assertNull(nextAssignedTask);
    }
    
    @Test
    @Deployment(resources = "process-with-froms-from-deployment.bpmn")
    public void testInitializeFormKeyForAvailableList() {
        String processInstanceId = runtimeService()
                .startProcessInstanceByKey("processWithFormsFromDeployment")
                .getId();
        when(identityService.userGroups()).thenReturn(Arrays.asList(""));
        when(identityService.userId()).thenReturn("testUser");
        
        TaskRepresentation actual = taskSvc.listAvailable(new TaskQueryParams())
            .stream()
            .filter(task -> task.getProcessInstanceId().equals(processInstanceId))
            .findAny()
            .get();
        
        assertEquals("embedded:deployment:forms/simpleTaskForm", actual.getFormKey());
    }

    @Test
    @Deployment(resources = "process-with-froms-from-deployment.bpmn")
    public void testInitializeFormKeyForAssignedList() {
        ProcessInstance processInstance = runtimeService()
                .startProcessInstanceByKey("processWithFormsFromDeployment");
        taskService().claim(task(processInstance).getId(), "testUser");
        when(identityService.userId()).thenReturn("testUser");
        
        TaskRepresentation actual = taskSvc.listAssigned(new TaskQueryParams())
                .stream()
                .filter(task -> task.getProcessInstanceId().equals(processInstance.getId()))
                .findAny()
                .get();
        
        assertEquals("embedded:deployment:forms/simpleTaskForm", actual.getFormKey());
    }

}
