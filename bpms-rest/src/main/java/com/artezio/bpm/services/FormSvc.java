package com.artezio.bpm.services;

import com.artezio.bpm.integration.CamundaFileStorage;
import com.artezio.bpm.resources.AbstractResourceLoader;
import com.artezio.forms.FormClient;
import com.artezio.forms.resources.ResourceLoader;
import com.artezio.forms.storages.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.task.Task;

import javax.inject.Inject;
import javax.inject.Named;
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

    public String getTaskFormWithData(String taskId, Map<String, Object> variables, String formResourcesDirectory) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = getDeploymentIdFromTask(taskId);
        ObjectNode data = variablesMapper.toJsonNode(variables);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDirectory);
        FileStorage fileStorage = new CamundaFileStorage(taskId);
        return formClient.getFormWithData(formKey, data, resourceLoader, fileStorage);
    }

    public String getStartFormWithData(String processDefinitionId, Map<String, Object> variables, String formResourcesDirectory) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = getDeploymentIdFromProcessDefinition(processDefinitionId);
        ObjectNode data = variablesMapper.toJsonNode(variables);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDirectory);
        return formClient.getFormWithData(formKey, data, resourceLoader);
    }

    public String dryValidationAndCleanupTaskForm(String taskId, Map<String, Object> formVariables,
                                                  Map<String, Object> taskVariables, String formResourcesDir, FileStorage fileStorage) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = getDeploymentIdFromTask(taskId);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDir);
        ObjectNode processVariablesJson = variablesMapper.toJsonNode(taskVariables);
        ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
        return formClient.dryValidationAndCleanup(formKey, formVariablesJson, processVariablesJson, resourceLoader, fileStorage);
    }

    public String dryValidationAndCleanupStartForm(String processDefinitionId, Map<String, Object> formVariables,
                                                   String formResourcesDir, FileStorage fileStorage) {
        String formKey = getStartFormKey(processDefinitionId);
        String deploymentId = getDeploymentIdFromProcessDefinition(processDefinitionId);
        ObjectNode formVariablesJson = variablesMapper.toJsonNode(formVariables);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDir);
        return formClient.dryValidationAndCleanup(formKey, formVariablesJson, JSON_MAPPER.createObjectNode(),
                resourceLoader, fileStorage);
    }

    private String getDeploymentIdFromProcessDefinition(String processDefinitionId) {
        return formService.getStartFormData(processDefinitionId).getDeploymentId();
    }

    public boolean shouldProcessSubmittedData(String taskId, String decision, String formResourcesDir) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = getDeploymentIdFromTask(taskId);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDir);
        return formClient.shouldProcessSubmission(formKey, decision, resourceLoader);
    }

    public List<String> getRootTaskFormFieldNames(String taskId, String formResourcesDir) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = getDeploymentIdFromTask(taskId);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDir);
        return formClient.getRootFormFieldNames(formKey, resourceLoader);
    }

    public List<String> getTaskFormFieldPaths(String taskId, String formResourcesDir) {
        String formKey = getTaskFormKey(taskId);
        String deploymentId = getDeploymentIdFromTask(taskId);
        ResourceLoader resourceLoader = AbstractResourceLoader.getResourceLoader(deploymentId, formKey, formResourcesDir);
        return formClient.getFormFieldPaths(formKey, resourceLoader);
    }

    private String getDeploymentIdFromTask(String taskId) {
        return formService.getTaskFormData(taskId).getDeploymentId();
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
