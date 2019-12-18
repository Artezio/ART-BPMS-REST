package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import com.artezio.forms.formio.exceptions.FormNotFoundException;
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

    public boolean shouldProcessSubmittedData(String taskId, String decision) {
        String formKey = getTaskFormKey(taskId);
        return formClient.shouldProcessSubmittedData(formKey, decision);
    }

    private String getTaskFormKey(String taskId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        String formKey = task.getProcessDefinitionId() != null
                ? getProcessTaskFormKey(task)
                : getCaseTaskFormKey(task);
        return formKey;
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

    private String getLatestDeploymentId() {
        return repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .get(0)
                .getId();
    }

    private String withoutDeploymentPrefix(String formKey) {
        return formKey.replaceFirst("deployment:", "");
    }

}
