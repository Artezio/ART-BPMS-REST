package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import org.camunda.bpm.engine.FormService;
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
    private FormService formService;

    public String getTaskFormWithData(String taskId, Map<String, Object> variables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        return formClient.getFormWithData(deploymentId, formKey, variables);
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        return formClient.getFormWithData(deploymentId, formKey, variables);
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> variables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        return formClient.dryValidationAndCleanup(deploymentId, formKey, variables);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> variables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        return formClient.dryValidationAndCleanup(deploymentId, formKey, variables);
    }

    public boolean shouldProcessSubmittedData(String taskId, String decision) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        return formClient.shouldProcessSubmission(deploymentId, formKey, decision);
    }

    private String getTaskFormKey(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        return task.getProcessDefinitionId() != null
                ? getProcessTaskFormKey(task)
                : getCaseTaskFormKey(task);
    }

    private String getProcessTaskFormKey(Task task) {
        String processDefinitionId = task.getProcessDefinitionId();
        return formService.getTaskFormKey(processDefinitionId, task.getTaskDefinitionKey());
    }

    private String getCaseTaskFormKey(Task task) {
        return formService.getTaskFormData(task.getId()).getFormKey();
    }

    private String getStartFormKey(String processDefinitionId) {
        return formService.getStartFormKey(processDefinitionId);
    }

}
