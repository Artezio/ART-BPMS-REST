package com.artezio.bpm.services;

import com.artezio.formio.client.FormClient;
import com.artezio.formio.client.exceptions.FormNotFoundException;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named
public class FormSvc {

    @Inject
    private FormClient formClient;
    @Inject
    private TaskService taskService;
    @Inject
    private RepositoryService repositoryService;
    @Inject
    private FormService formService;

    public String getTaskFormWithData(String taskId, Map<String, Object> variables) throws FormNotFoundException {
        String formKey = getTaskFormKey(taskId);
        return formClient.getFormWithData(formKey, variables);
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables) throws FormNotFoundException {
        String formKey = getStartFormKey(processDefinitionId);
        return formClient.getFormWithData(formKey, variables);
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> variables) throws FormNotFoundException {
        String formKey = getTaskFormKey(taskId);
        return formClient.dryValidationAndCleanup(formKey, variables);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> variables) throws FormNotFoundException {
        String formKey = getStartFormKey(processDefinitionId);
        return formClient.dryValidationAndCleanup(formKey, variables);
    }

    public boolean shouldSkipValidation(String taskId, String submissionState) {
        String formKey = getTaskFormKey(taskId);
        return formClient.shouldSkipValidation(formKey, submissionState);
    }

    String getTaskFormDefinition(String taskId) {
        String formKey = getTaskFormKey(taskId);
        return formClient.getFormDefinition(formKey).toString();
    }

    String getStartFormDefinition(String processDefinitionId) {
        String formKey = getStartFormKey(processDefinitionId);
        return formClient.getFormDefinition(formKey).toString();
    }

    private String getTaskFormKey(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        String processDefinitionId = task.getProcessDefinitionId();
        String formKey = formService.getTaskFormKey(processDefinitionId, task.getTaskDefinitionKey());
        return getFormKeyWithVersion(withoutDeploymentPrefix(formKey));
    }

    private String getStartFormKey(String processDefinitionId) {
        String formKey = formService.getStartFormKey(processDefinitionId);
        return getFormKeyWithVersion(withoutDeploymentPrefix(formKey));
    }

    private String getLatestDeploymentId() {
        return repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .get(0)
                .getId();
    }

    private String getFormKeyWithVersion(String formKey) {
        String deploymentId = getLatestDeploymentId();
        return formKey + "-" + deploymentId;
    }

    private String withoutDeploymentPrefix(String formKey) {
        return formKey.replaceFirst("deployment:", "");
    }

}
