package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    @Inject
    private VariablesMapper variablesMapper;

    public String getTaskFormWithData(String taskId, Map<String, Object> variables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        return formClient.getFormWithData(formKey, deploymentId, data);
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        return formClient.getFormWithData(formKey, deploymentId, data);
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> variables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        return formClient.dryValidationAndCleanup(formKey, deploymentId, data);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> variables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        return formClient.dryValidationAndCleanup(formKey, deploymentId, data);
    }

    public boolean shouldProcessSubmittedData(String taskId, String decision) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        return formClient.shouldProcessSubmission(formKey, deploymentId, decision);
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
