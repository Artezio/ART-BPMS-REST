package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.bpm.services.integration.cdi.ConcreteImplementation;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.formio.client.exceptions.FormNotFoundException;
import com.artezio.logging.Log;
import com.jayway.jsonpath.JsonPath;
import io.swagger.v3.oas.annotations.Operation;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.engine.variable.impl.value.ObjectValueImpl;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;

import javax.annotation.security.PermitAll;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.artezio.bpm.services.VariablesMapper.EXTENSION_NAME_PREFIX;
import static com.artezio.logging.Log.Level.CONFIG;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Path("/task")
@Stateless
public class TaskSvc {

    private static final String DECISION_VARIABLE_NAME = "decision";
    private static final String STATE_VARIABLE_NAME = "state";

    @Inject
    private TaskService taskService;
    @Inject
    private IdentitySvc identityService;
    @Inject
    private FormService camundaFormService;
    @Inject
    private FormSvc formService;
    @Inject
    private VariablesMapper variablesMapper;
    @Inject
    private CaseService caseService;
    @Inject
    private RuntimeService runtimeService;
    @Inject
    @ConcreteImplementation
    private FileStorage fileStorage;
    @Inject
    private RepositoryService repositoryService;
    @Inject
    private VariableValidator variableValidator;

    @GET
    @Path("available")
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of available tasks")
    public List<TaskRepresentation> listAvailable() {
        return taskService.createTaskQuery()
                .or()
                .taskCandidateGroupIn(identityService.userGroups())
                .taskCandidateUser(identityService.userId())
                .endOr()
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @GET
    @Path("assigned")
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of assigned task")
    public List<TaskRepresentation> listAssigned() {
        return taskService.createTaskQuery()
                .taskAssignee(identityService.userId())
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @POST
    @Path("{task-id}/claim")
    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Claiming task '{0}'")
    public void claim(@PathParam("task-id") @Valid @NotNull String taskId) {
        ensureUserHasAccess(taskId);
        taskService.claim(taskId, identityService.userId());
    }

    @GET
    @Path("{task-id}/form")
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Log(beforeExecuteMessage = "Loading form for user task '{0}'", afterExecuteMessage = "Form successfully loaded")
    public String loadForm(@PathParam("task-id") @Valid @NotNull String taskId) throws FormNotFoundException {
        ensureUserHasAccess(taskId);
        VariableMap taskVariables = taskService.getVariablesTyped(taskId);
        return formService.getTaskFormWithData(taskId, taskVariables);
    }

    @GET
    @Path("{task-id}/file/")
    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Downloading file '{1}'", afterExecuteMessage = "File '{1}' successfully downloaded")
    public Response downloadFile(@PathParam("task-id") @Valid @NotNull String taskId,
                                 @QueryParam(value = "filePath") @Valid @NotNull String filePath) {
        ensureUserHasAccess(taskId);
        try {
            Map<String, Object> file = getRequestedFileVariableValue(taskId, filePath);
            String type = StringUtils.isNotEmpty((String) file.get("type")) ? (String) file.get("type") : MediaType.APPLICATION_OCTET_STREAM;
            byte[] fileContent = getFileContentFromUrl((String) file.get("url"));
            return Response
                    .ok(fileContent, type)
                    .header("Content-Disposition", "attachment; filename=" + file.get("originalName"))
                    .build();
        } catch (RuntimeException exception) {
            throw new NotFoundException("File '" + filePath + "' is not found.");
        }
    }

    @POST
    @Path("{task-id}/complete")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Operation(description = "Complete a task using input variables. If an execution doesn't have some of input variables, they are ignored. " +
            "Note: if two or more files with identical names are uploaded in one variable, the only one of these " +
            "files will be used and it is not determined which one will be chosen. " +
            "Returns next assigned task if available")
    @Log(level = CONFIG, beforeExecuteMessage = "Completing task '{0}'", afterExecuteMessage = "Task '{0}' successfully completed")
    public TaskRepresentation complete(@PathParam("task-id") @Valid @NotNull String taskId,
                                       Map<String, Object> inputVariables) throws IOException, FormNotFoundException {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        ensureAssigned(taskId);
        inputVariables = validateAndMergeToTaskVariables(taskId, inputVariables);
        Map<String, String> processExtensions = getProcessExtensions(taskId, task);
        inputVariables = variablesMapper.convertVariablesToEntities(inputVariables, processExtensions);
        variableValidator.validate(inputVariables);
        taskService.complete(taskId, inputVariables);
        Task nextAssignedTask = getNextAssignedTask(task);
        return TaskRepresentation.fromEntity(nextAssignedTask);
    }

    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting next assigned task for process instance")
    public Task getNextAssignedTask(String processInstanceId) {
        List<Task> assignedTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(identityService.userId())
                .list();
        return assignedTasks.size() == 1
                ? assignedTasks.get(0)
                : null;
    }

    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting next assigned task for case execution")
    public Task getNextAssignedTask(CaseExecution caseExecution) {
        List<Task> assignedTasks = taskService.createTaskQuery()
                .caseInstanceId(caseExecution.getCaseInstanceId())
                .taskAssignee(identityService.userId())
                .list();
        return assignedTasks.size() == 1
                ? assignedTasks.get(0)
                : null;
    }

    protected ProcessInstance getProcessInstance(Task task) {
        return runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
    }

    protected CaseExecution getCaseExecution(Task task) {
        return caseService.createCaseExecutionQuery()
                .caseInstanceId(task.getCaseInstanceId())
                .singleResult();
    }

    private Map<String, Object> validateAndMergeToTaskVariables(String taskId, Map<String, Object> inputVariables) throws IOException {
        String state = (String) inputVariables.get(STATE_VARIABLE_NAME);
        inputVariables = !skipValidation(taskId, state)
                ? validateAndMergeToTaskVariables(inputVariables, taskId)
                : new HashMap<>();
        inputVariables.remove(STATE_VARIABLE_NAME);
        inputVariables.put(DECISION_VARIABLE_NAME, state);
        return inputVariables;
    }

    private Map<String, String> getProcessExtensions(String taskId, Task task) {
        return task.getProcessDefinitionId() != null
                ? getProcessExtensions(taskId)
                : Collections.emptyMap();
    }

    private Task getNextAssignedTask(Task task) {
        return task.getProcessDefinitionId() != null
                ? getNextAssignedTask(task.getProcessInstanceId())
                : getNextAssignedTask(getCaseExecution(task));
    }

    private Map<String, Object> getRequestedFileVariableValue(String taskId, String filePath) {
        String[] splitFilePath = filePath.split("/");
        String sanitizedVariableName = cleanUpVariableName(splitFilePath[0]);
        if (splitFilePath.length > 1) {
            String variableJsonValue = ((ObjectValueImpl) taskService.getVariableTyped(taskId, sanitizedVariableName, false))
                    .getValueSerialized();
            return getFileValue(splitFilePath, variableJsonValue);
        } else {
            return taskService.getVariableTyped(taskId, sanitizedVariableName);
        }
    }

    private Map<String, String> getProcessExtensions(String taskId) {
        String processDefinitionId = taskService.createTaskQuery().taskId(taskId).singleResult().getProcessDefinitionId();
        String processDefinitionKey = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult()
                .getKey();
        BpmnModelInstance bpmnModelInstance = repositoryService.getBpmnModelInstance(processDefinitionId);
        Process processElement = bpmnModelInstance.getModelElementById(processDefinitionKey);
        ExtensionElements extensionElements = processElement.getExtensionElements();
        return extensionElements != null
                ? extensionElements.getElements().stream()
                .flatMap(extensionElement -> ((CamundaProperties) extensionElement).getCamundaProperties().stream())
                .filter(extension -> extension.getCamundaName().startsWith(EXTENSION_NAME_PREFIX))
                .collect(Collectors.toMap(CamundaProperty::getCamundaName, CamundaProperty::getCamundaValue))
                : emptyMap();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getFileValue(String[] splitFilePath, String variableJsonValue) {
        String jaywayFilePath = String.join(".", copyOfRange(splitFilePath, 1, splitFilePath.length));
        JSONArray fileValues = JsonPath.read(variableJsonValue, "$.." + jaywayFilePath);
        return (Map<String, Object>) fileValues.get(0);
    }

    private byte[] getFileContentFromUrl(String url) {
        String base64EncodedFileContent = extractContentFromUrl(url);
        return decodeFromBase64(base64EncodedFileContent);
    }

    private String extractContentFromUrl(String url) {
        return url.split(",")[1];
    }

    private byte[] decodeFromBase64(String encodedString) {
        return Base64.getMimeDecoder().decode(encodedString);
    }

    private String cleanUpVariableName(String variableName) {
        return variableName.replaceAll("\\W", "");
    }

    private boolean skipValidation(String taskId, String decision) {
        return Boolean.parseBoolean(formService.interpretPropertyForState(taskId, "skipValidation", decision));
    }

    private Map<String, Object> validateAndMergeToTaskVariables(Map<String, Object> inputVariables, String taskId)
            throws IOException, FormNotFoundException {
        String formKey = camundaFormService.getTaskFormData(taskId).getFormKey();
        if (formKey != null) {
            String cleanDataJson = formService.dryValidationAndCleanupTaskForm(taskId, inputVariables);
            Map<String, Object> taskVariables = taskService.getVariables(taskId);
            variablesMapper.updateVariables(taskVariables, cleanDataJson);
            return taskVariables;
        } else {
            return inputVariables;
        }
    }

    private void ensureAssigned(@NotNull String taskId) {
        if (taskService.createTaskQuery()
                .taskId(taskId)
                .taskAssignee(identityService.userId())
                .list()
                .isEmpty()) {
            throw new NotAuthorizedException();
        }
    }

    private void ensureUserHasAccess(@NotNull String taskId) {
        if (taskService.createTaskQuery()
                .taskId(taskId)
                .or()
                .taskCandidateGroupIn(identityService.userGroups())
                .taskCandidateUser(identityService.userId())
                .taskAssignee(identityService.userId())
                .endOr()
                .list()
                .isEmpty()) {
            throw new NotAuthorizedException();
        }
    }

}
