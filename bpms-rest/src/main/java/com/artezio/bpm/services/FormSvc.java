package com.artezio.bpm.services;

import com.artezio.bpm.resources.AbstractResourceLoader;
import com.artezio.bpm.resources.AppResourceLoader;
import com.artezio.bpm.resources.DeploymentResourceLoader;
import com.artezio.bpm.resources.ResourceLoader;
import com.artezio.forms.FormClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.ServletContext;
import java.util.List;
import java.util.Map;

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
    private ServletContext servletContext;

    public String getTaskFormWithData(String taskId, Map<String, Object> variables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, formKey);
        return formClient.getFormWithData(formKey, data, resourceLoader);
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode data = variablesMapper.toJsonNode(variables);
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, formKey);
        return formClient.getFormWithData(formKey, data, resourceLoader);
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> formVariables) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, formKey);
        List<String> formVariableNames = formClient.getFormVariableNames(formKey, resourceLoader);
        ObjectNode processVariablesJson = variablesMapper.toJsonNode(taskService.getVariables(taskId, formVariableNames));
        ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
        return formClient.dryValidationAndCleanup(formKey, formVariablesJson, processVariablesJson, resourceLoader);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> formVariables) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = formService.getStartFormData(processDefinitionId).getDeploymentId();
        ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, formKey);
        return formClient.dryValidationAndCleanup(formKey, formVariablesJson, JSON_MAPPER.createObjectNode(), resourceLoader);
    }

    public boolean shouldProcessSubmittedData(String taskId, String decision) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = formService.getTaskFormData(taskId).getDeploymentId();
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, formKey);
        return formClient.shouldProcessSubmission(formKey, decision, resourceLoader);
    }

    private ResourceLoader getResourceLoader(String deploymentId, String formKey) {
        return AbstractResourceLoader.getProtocol(formKey).equals(AbstractResourceLoader.DEPLOYMENT_PROTOCOL)
                ? new DeploymentResourceLoader(deploymentId)
                : new AppResourceLoader(servletContext);
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
