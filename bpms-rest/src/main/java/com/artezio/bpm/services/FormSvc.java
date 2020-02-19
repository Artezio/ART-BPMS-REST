package com.artezio.bpm.services;

import com.artezio.forms.FormClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Named
public class FormSvc {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Inject
    private FormClient formClient;
    @Inject
    private TaskService taskService;
    @Inject
    private FormService formService;
    @Inject
    private VariablesMapper variablesMapper;
    @Inject
    private DeploymentSvc deploymentSvc;

    public String getTaskFormWithData(String taskId, Map<String, Object> variables) throws IOException {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        Map<String, InputStream> publicResources = null;
        try (InputStream form = deploymentSvc.getResource(deploymentId, formKey)) {
            publicResources = getPublicResources(formKey, deploymentId);
            return formClient.getFormWithData(form, data, publicResources);
        } finally {
            closeResources(publicResources.values());
        }
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables) throws IOException {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        Map<String, InputStream> publicResources = null;
        try (InputStream form = deploymentSvc.getResource(deploymentId, formKey)) {
            publicResources = getPublicResources(formKey, deploymentId);
            return formClient.getFormWithData(form, data, publicResources);
        } finally {
            closeResources(publicResources.values());
        }
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> formVariables) throws IOException {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        Map<String, InputStream> publicResources = null;
        try (InputStream form = deploymentSvc.getResource(deploymentId, formKey)) {
            List<String> formVariableNames = formClient.getFormVariableNames(form);
            ObjectNode processVariablesJson = variablesMapper.toJsonNode(taskService.getVariables(taskId, formVariableNames));
            ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
            publicResources = getPublicResources(formKey, deploymentId);
            return formClient.dryValidationAndCleanup(form, formVariablesJson, processVariablesJson, publicResources);
        } finally {
            closeResources(publicResources.values());
        }
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> formVariables) throws IOException {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
        Map<String, InputStream> publicResources = null;
        try(InputStream form = deploymentSvc.getResource(deploymentId, formKey)) {
            publicResources = getPublicResources(formKey, deploymentId);
            return formClient.dryValidationAndCleanup(form, formVariablesJson, JSON_MAPPER.createObjectNode(), publicResources);
        } finally {
            closeResources(publicResources.values());
        }
    }

    public boolean shouldProcessSubmittedData(String taskId, String decision) throws IOException {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        try (InputStream form = deploymentSvc.getResource(deploymentId, formKey)) {
            return formClient.shouldProcessSubmission(form, decision);
        }
    }

    private Map<String, InputStream> getPublicResources(String formKey, String deploymentId) {
        return deploymentSvc.listResourceNames(deploymentId, formKey).stream()
                .collect(Collectors.toMap(resourceName -> resourceName, resourceName -> {
                    try {
                        return deploymentSvc.getResource(deploymentId, resourceName);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }));
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

    private void closeResources(Collection<InputStream> values) {
        values.forEach(value -> {
            try {
                if (value != null) value.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
