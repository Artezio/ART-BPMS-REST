package com.artezio.bpm.services;

import com.artezio.formio.client.FormClient;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class FormSvcTest extends ServiceTest {

    @InjectMocks
    private FormSvc formSvc = new FormSvc();
    @Mock
    private FormClient formClient;

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
    public void testToValidFormioIdentifier() {
        final String identifier = "Some identifier: for task, maybe? / Or process";

        String actual = FormClient.toValidFormioIdentifier(identifier);

        assertTrue(actual.matches("[a-zA-Z0-9-]*"));
    }

    @Test
    public void testToValidFormioPath() {
        final String identifier = "Process 1/subprocess:2/task:15900_1";

        String actual = FormClient.toValidFormioPath(identifier);

        assertTrue(actual.matches("[a-zA-Z0-9-/]*"));
    }

    @Test
    public void testGetTaskFormWithData() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");
        Task task = getTaskService().createTaskQuery().taskUnassigned().singleResult();
        String expectedFormPath = "Form_1";
        String expectedResult = "{someFormWithData}";

        when(formClient.getFormWithData(eq(expectedFormPath), any())).thenReturn(expectedResult);

        String actual = formSvc.getTaskFormWithData(task.getId(), Collections.emptyMap());

        assertEquals(expectedResult, actual);
    }

    @Test
    public void testGetStartFormWithData() throws IOException {
        Deployment deployment = createDeployment("test-deployment", "test-process-with-start-form.bpmn");
        ProcessInstance processInstance = getRuntimeService().startProcessInstanceByKey("testProcessWithStartForm");
        String expectedFormPath = FormClient.toValidFormioPath("testStartForm");
        String expectedResult = "{someFormWithData}";

        when(formClient.getFormWithData(eq(expectedFormPath), any())).thenReturn(expectedResult);

        String actual = formSvc.getStartFormWithData(processInstance.getProcessDefinitionId(), Collections.emptyMap());

        assertEquals(expectedResult, actual);
    }

    @Test
    public void testShouldSkipValidation_StateWithValidationIsPassed() throws IOException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");

        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String submissionState = "stateWithValidation";
        String formKey = "Form_1";

        when(formClient.shouldSkipValidation(formKey, submissionState)).thenReturn(false);

        boolean actual = formSvc.shouldSkipValidation(taskId, submissionState);

        assertFalse(actual);
    }

    @Test
    public void testShouldSkipValidation_StateWithoutValidationIsPassed() throws IOException {
        createDeployment("test-deployment", "test-process-containing-task-with-form.bpmn");
        getRuntimeService().startProcessInstanceByKey("Process_contains_TaskWithForm");

        String taskId = getTaskService().createTaskQuery().taskDefinitionKey("Task_1").singleResult().getId();
        String submissionState = "stateWithoutValidation";
        String formKey = "Form_1";

        when(formClient.shouldSkipValidation(formKey, submissionState)).thenReturn(true);

        boolean actual = formSvc.shouldSkipValidation(taskId, submissionState);

        assertTrue(actual);
    }

}
