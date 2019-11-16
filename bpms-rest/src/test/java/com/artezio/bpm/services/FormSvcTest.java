package com.artezio.bpm.services;

import com.artezio.forms.formio.FormioClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentQuery;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertTrue;
import static org.codehaus.groovy.runtime.InvokerHelper.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class FormSvcTest extends ServiceTest {

    @InjectMocks
    private FormSvc formSvc = new FormSvc();
    @Mock
    private FormioClient formioClient;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryService repositoryService;

    @Before
    public void init() throws NoSuchFieldException {
        Field formServiceField = formSvc.getClass().getDeclaredField("formService");
        Field taskServiceField = formSvc.getClass().getDeclaredField("taskService");
        setField(formSvc, formServiceField, getFormService());
        setField(formSvc, taskServiceField, getTaskService());
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
    @Ignore
    public void testGetTaskFormWithData() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        Task task = getTaskService().createTaskQuery().taskUnassigned().singleResult();
        String expectedFormPath = "Form_1";
        String expectedResult = "{someFormWithData}";

        when(formioClient.getFormWithData(eq(expectedFormPath), any())).thenReturn(expectedResult);

        String actual = formSvc.getTaskFormWithData(task.getId(), Collections.emptyMap());

        assertEquals(expectedResult, actual);
    }

    @Test
    @Ignore
    public void testGetStartFormWithData() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-with-start-form.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("testProcessWithStartForm");
        String expectedResult = "{someFormWithData}";
        String expectedFormPath = "";

        when(formioClient.getFormWithData(eq(expectedFormPath), any())).thenReturn(expectedResult);

        String actual = formSvc.getStartFormWithData(processInstance.getProcessDefinitionId(), Collections.emptyMap());

        assertEquals(expectedResult, actual);
    }

    @Test
    //TODO Replace 'when' part of scenario for repositoryService with method chain invocations.
    // Problem description: 'when' part of scenario for repositoryService, which is RETURN_DEEP_STUBS, is split into
    // several steps instead of using a chain method invocations because method 'desc()' returns generic type, hence
    // mockito has problem with class cast.
    public void testShouldProcessSubmittedData_DecisionWithValidation() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithState.json"));

        String deploymentId = deployment.getId();
        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String decision = "submitted";
        String formKey = "Form_1-" + deploymentId;

        DeploymentQuery deploymentQuery = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.orderByDeploymentTime()).thenReturn(deploymentQuery);
        when(deploymentQuery.desc()).thenReturn(deploymentQuery);
        when(deploymentQuery.list()).thenReturn(asList(deployment));
        when(formioClient.shouldProcessSubmittedData(formKey, decision)).thenReturn(true);

        boolean shouldSkipValidation = formSvc.shouldProcessSubmittedData(taskId, decision);

        assertTrue(shouldSkipValidation);
    }

    @Test
    //TODO Replace 'when' part of scenario for repositoryService with method chain invocations.
    // Problem description: 'when' part of scenario for repositoryService, which is RETURN_DEEP_STUBS, is split into
    // several steps instead of using a chain method invocations because method 'desc()' returns generic type, hence
    // mockito has problem with class cast.
    public void testShouldProcessSubmittedData_DecisionWithoutValidation() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithState.json"));

        String deploymentId = deployment.getId();
        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String decision = "canceled";
        String formKey = "Form_1-" + deploymentId;

        DeploymentQuery deploymentQuery = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);
        when(deploymentQuery.orderByDeploymentTime()).thenReturn(deploymentQuery);
        when(deploymentQuery.desc()).thenReturn(deploymentQuery);
        when(deploymentQuery.list()).thenReturn(asList(deployment));
        when(formioClient.shouldProcessSubmittedData(formKey, decision)).thenReturn(false);

        boolean actual = formSvc.shouldProcessSubmittedData(taskId, decision);

        assertFalse(actual);
    }

}
