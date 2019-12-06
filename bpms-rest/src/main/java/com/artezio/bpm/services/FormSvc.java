package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import com.artezio.forms.formio.FormComponent;
import com.artezio.forms.formio.exceptions.FormNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Named
public class FormSvc {

    private final static boolean IS_FORM_VERSIONING_ENABLED = Boolean.parseBoolean(System.getProperty("FORM_VERSIONING", "true"));

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
    
    //TODO rename params
    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> variables)
            throws FormNotFoundException, JsonProcessingException {
        String formKey = getTaskFormKey(taskId);
        Collection<String> taskVariableNames = formClient.getFormVariableNames(formKey);
        Map<String, Object> taskVariables = taskService.getVariables(taskId, taskVariableNames);
        return formClient.dryValidationAndCleanup(formKey, variables, taskVariables);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> variables) throws FormNotFoundException {
        String formKey = getStartFormKey(processDefinitionId);
        return formClient.dryValidationAndCleanup(formKey, variables, null);
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
        return getFormKey(withoutDeploymentPrefix(formKey));
    }

    private String getProcessTaskFormKey(Task task) {
        String processDefinitionId = task.getProcessDefinitionId();
        return formService.getTaskFormKey(processDefinitionId, task.getTaskDefinitionKey());
    }

    private String getCaseTaskFormKey(Task task) {
        return formService.getTaskFormData(task.getId()).getFormKey();
    }

    private String getStartFormKey(String processDefinitionId) {
        String formKey = formService.getStartFormKey(processDefinitionId);
        return getFormKey(withoutDeploymentPrefix(formKey));
    }

    private String getLatestDeploymentId() {
        return repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .get(0)
                .getId();
    }

    private String getFormKey(String formKey) {
        if (!IS_FORM_VERSIONING_ENABLED) {
            return formKey;
        }
        String deploymentId = getLatestDeploymentId();
        return formKey + "-" + deploymentId;
    }

    private String withoutDeploymentPrefix(String formKey) {
        return formKey.replaceFirst("deployment:", "");
    }

}
