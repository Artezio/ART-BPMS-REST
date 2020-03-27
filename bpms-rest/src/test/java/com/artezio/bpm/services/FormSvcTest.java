package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import com.artezio.forms.resources.ResourceLoader;
import com.artezio.forms.storages.FileStorage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import junitx.framework.ListAssert;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CDI.class)
@PowerMockIgnore({"com.sun.org.apache.*", "javax.xml.*", "java.xml.*", "org.xml.*", "org.w3c.dom.*", "javax.management.*"})
public class FormSvcTest extends ServiceTest {

    @InjectMocks
    private FormSvc formSvc = new FormSvc();
    @Mock
    private FormClient formClient;
    @Mock
    private VariablesMapper variablesMapper;

    @Before
    public void init() throws NoSuchFieldException {
        Field formServiceField = formSvc.getClass().getDeclaredField("formService");
        Field taskServiceField = formSvc.getClass().getDeclaredField("taskService");
        setField(formSvc, formServiceField, getFormService());
        setField(formSvc, taskServiceField, getTaskService());
        PowerMockito.mockStatic(CDI.class);
        CDI cdiContext = mock(CDI.class);
        Instance<ServletContext> servletContextInstance = mock(Instance.class);
        when(CDI.current()).thenReturn(cdiContext);
        when(cdiContext.select(ServletContext.class)).thenReturn(servletContextInstance);
        when(servletContextInstance.get()).thenReturn(null);
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
    public void testGetTaskFormWithData() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        Task task = getTaskService().createTaskQuery().taskUnassigned().singleResult();
        Map<String, Object> taskVariables = Collections.emptyMap();
        ObjectNode taskVariablesNode = JsonNodeFactory.instance.objectNode();
        String formKey = "Form_1";
        String expected = "{someFormWithData}";

        when(variablesMapper.toJsonNode(taskVariables)).thenReturn(taskVariablesNode);
        when(formClient.getFormWithData(eq(formKey), eq(taskVariablesNode), any(ResourceLoader.class), any(FileStorage.class))).thenReturn(expected);

        String actual = formSvc.getTaskFormWithData(task.getId(), taskVariables, PUBLIC_RESOURCES_DIRECTORY);

        assertEquals(expected, actual);
    }

    @Test
    public void testGetStartFormWithData() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-with-start-form.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("testProcessWithStartForm");
        String formKey = "testStartForm";
        String expected = "{someFormWithData}";
        Map<String, Object> taskVariables = Collections.emptyMap();
        ObjectNode taskVariablesNode = JsonNodeFactory.instance.objectNode();

        when(variablesMapper.toJsonNode(taskVariables)).thenReturn(taskVariablesNode);
        when(formClient.getFormWithData(eq(formKey), eq(taskVariablesNode), any(ResourceLoader.class))).thenReturn(expected);

        String actual = formSvc.getStartFormWithData(processInstance.getProcessDefinitionId(), taskVariables, PUBLIC_RESOURCES_DIRECTORY);

        assertEquals(expected, actual);
    }

    @Test
    public void testShouldProcessSubmittedData_DecisionWithValidation() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");

        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String decision = "submitted";
        String formKey = "Form_1";

        when(formClient.shouldProcessSubmission(eq(formKey), eq(decision), any(ResourceLoader.class))).thenReturn(true);

        boolean shouldSkipValidation = formSvc.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY);

        assertTrue(shouldSkipValidation);
    }

    @Test
    public void testShouldProcessSubmittedData_DecisionWithoutValidation() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");

        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String decision = "canceled";
        String formKey = "Form_1";

        when(formClient.shouldProcessSubmission(eq(formKey), eq(decision), any(ResourceLoader.class))).thenReturn(false);

        boolean actual = formSvc.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY);

        assertFalse(actual);
    }

    @Test
    public void testGetRootTaskFormFieldNames() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String formKey = "Form_1";
        List<String> expected = asList("formField1", "formField2");

        when(formClient.getRootFormFieldNames(eq(formKey), any(ResourceLoader.class))).thenReturn(expected);

        List<String> actual = formSvc.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY);

        ListAssert.assertEquals(expected, actual);
    }

    @Test
    public void testGetTaskFormFieldPaths() throws IOException, URISyntaxException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String formKey = "Form_1";
        List<String> expected = asList("field1", "field11", "field2");

        when(formClient.getFormFieldPaths(eq(formKey), any(ResourceLoader.class))).thenReturn(expected);

        List<String> actual = formSvc.getTaskFormFieldPaths(taskId, PUBLIC_RESOURCES_DIRECTORY);

        ListAssert.assertEquals(expected, actual);
    }

}
