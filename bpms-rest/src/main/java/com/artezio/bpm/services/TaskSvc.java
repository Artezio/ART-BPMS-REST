package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.rest.query.task.TaskQueryParams;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.bpm.services.integration.FileStorage;
import com.artezio.bpm.services.integration.cdi.ConcreteImplementation;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.logging.Log;
import com.jayway.jsonpath.JsonPath;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import net.minidev.json.JSONArray;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.artezio.bpm.services.VariablesMapper.EXTENSION_NAME_PREFIX;
import static com.artezio.logging.Log.Level.CONFIG;
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
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
    @Operation(
            description = "List of tasks available to the user who is completing this request.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            parameters = {
                    @Parameter(
                            name = "dueDate",
                            in = QUERY,
                            description = "Restrict to tasks that are due on the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueDateExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due on the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "dueAfter",
                            in = QUERY,
                            description = "Restrict to tasks that are due after the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueAfterExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due after the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "dueBefore",
                            in = QUERY,
                            description = "Restrict to tasks that are due before the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueBeforeExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due before the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "followUpDate",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date on the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "followUpDateExpression",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date on the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "followUpAfter",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date after the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "followUpAfterExpression",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date after the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "followUpBefore",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date before the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "followUpBeforeExpression",
                            in = QUERY,
                            description = "Restrict to tasks that have a followUp date before the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "followUpBeforeOrNotExistent",
                            in = QUERY,
                            description = "Restrict to tasks that have no followUp date or a followUp date before the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "followUpBeforeOrNotExistentExpression",
                            in = QUERY,
                            description = "Restrict to tasks that have no followUp date or a followUp date before the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            content = @Content(
                                    mediaType = APPLICATION_JSON,
                                    array = @ArraySchema(schema = @Schema(implementation = TaskRepresentation.class))
                            )
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of available tasks")
    public @ApiResponse List<TaskRepresentation> listAvailable(@BeanParam TaskQueryParams queryParams) {
        TaskQuery taskQuery = createTaskQuery(queryParams)
                .or()
                .taskCandidateGroupIn(identityService.userGroups())
                .taskCandidateUser(identityService.userId())
                .endOr();

        return taskQuery
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @GET
    @Path("assigned")
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Operation(
            description = "List of tasks assigned to the user who is completing this request.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            parameters = {
                    @Parameter(
                            name = "dueDate",
                            in = QUERY,
                            description = "Restrict to tasks that are due on the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueDateExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due on the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "dueAfter",
                            in = QUERY,
                            description = "Restrict to tasks that are due after the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueAfterExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due after the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    ),
                    @Parameter(
                            name = "dueBefore",
                            in = QUERY,
                            description = "Restrict to tasks that are due before the given date.",
                            schema = @Schema(format = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"),
                            example = "2013-01-23T14:42:45.546+0200"
                    ),
                    @Parameter(
                            name = "dueBeforeExpression",
                            in = QUERY,
                            description = "Restrict to tasks that are due before the date described by the given expression." +
                                    " The expression must evaluate to a java.util.Date or org.joda.time.DateTime object.",
                            example = "${now()}"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            content = @Content(
                                    mediaType = APPLICATION_JSON,
                                    array = @ArraySchema(schema = @Schema(implementation = TaskRepresentation.class))
                            )
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of assigned task")
    public List<TaskRepresentation> listAssigned(@BeanParam TaskQueryParams queryParams) {
        TaskQuery taskQuery = createTaskQuery(queryParams)
                .taskAssignee(identityService.userId());

        return taskQuery
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @POST
    @Path("{task-id}/claim")
    @PermitAll
    @Operation(
            description = "Assign a task to the user who is completing this request.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "204"
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "A user doesn't have an access to assign the task with specified id."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Claiming task '{0}'")
    public void claim(
            @Parameter(description = "The id of the task which is going to be assigned", required = true) @PathParam("task-id") @Valid @NotNull String taskId) {
        ensureUserHasAccess(taskId);
        taskService.claim(taskId, identityService.userId());
    }

    @GET
    @Path("{task-id}/form")
    @Produces(APPLICATION_JSON)
    @PermitAll
    @Operation(
            description = "Load a form for a task.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Form definition for the task with specified id.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "A user doesn't have an access to load a form for the task with specified id."
                    )
            }
    )
    @Log(beforeExecuteMessage = "Loading form for user task '{0}'", afterExecuteMessage = "Form successfully loaded")
    public String loadForm(
            @Parameter(description = "The id of the task which form is requested for.", required = true) @PathParam("task-id") @Valid @NotNull String taskId) {
        ensureUserHasAccess(taskId);
        VariableMap taskVariables = taskService.getVariablesTyped(taskId);
        return formService.getTaskFormWithData(taskId, taskVariables);
    }

    @GET
    @Path("{task-id}/file/")
    @PermitAll
    @Operation(
            description = "Download a file which is a variable in the scope of a task.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Requested file is found."
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "A user doesn't have an access to download the file."
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Requested file is not found."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Downloading file '{1}'", afterExecuteMessage = "File '{1}' successfully downloaded")
    public Response downloadFile(
            @Parameter(description = "An id of the task which has in its scope requested file as variable.", required = true) @PathParam("task-id") @Valid @NotNull String taskId,
            @Parameter(description = "Path to requested file.", required = true) @QueryParam(value = "filePath") @Valid @NotNull String filePath) {
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
    @Operation(
            description = "Complete a task using input variables.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/task-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "There is a task assigned to a user.",
                            content = @Content(
                                    mediaType = APPLICATION_JSON,
                                    schema = @Schema(ref = "#/components/schemas/TaskRepresentation")
                            )
                    ),
                    @ApiResponse(
                            responseCode = "204",
                            description = "There is no tasks assigned to a user."
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "The user is not allowed to complete task with passed id."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Completing task '{0}'", afterExecuteMessage = "Task '{0}' successfully completed")
    public TaskRepresentation complete(
            @Parameter(description = "The id of the task to be completed.", required = true) @PathParam("task-id") @Valid @NotNull String taskId,
            @RequestBody(description = "The variables which will be passed to a process after completing the task.") Map<String, Object> inputVariables) throws IOException {
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

    protected CaseExecution getCaseExecution(Task task) {
        return caseService.createCaseExecutionQuery()
                .caseInstanceId(task.getCaseInstanceId())
                .singleResult();
    }

    private TaskQuery createTaskQuery(TaskQueryParams params) {
        TaskQuery query = taskService.createTaskQuery();
        applyDueDateFilters(query, params);
        applyFollowUpDateFilters(query, params);

        return query;
    }

    private void applyDueDateFilters(TaskQuery query, TaskQueryParams params) {
        addCriteriaIfExists(params.getDueDate(), query::dueDate);
        addCriteriaIfExists(params.getDueDateExpression(), query::dueDateExpression);
        addCriteriaIfExists(params.getDueAfter(), query::dueAfter);
        addCriteriaIfExists(params.getDueAfterExpression(), query::dueAfterExpression);
        addCriteriaIfExists(params.getDueBefore(), query::dueBefore);
        addCriteriaIfExists(params.getDueBeforeExpression(), query::dueBeforeExpression);
    }

    private void applyFollowUpDateFilters(TaskQuery query, TaskQueryParams params) {
        addCriteriaIfExists(params.getFollowUpDate(), query::followUpDate);
        addCriteriaIfExists(params.getFollowUpDateExpression(), query::followUpDateExpression);
        addCriteriaIfExists(params.getFollowUpAfter(), query::followUpAfter);
        addCriteriaIfExists(params.getFollowUpAfterExpression(), query::followUpAfterExpression);
        addCriteriaIfExists(params.getFollowUpBefore(), query::followUpBefore);
        addCriteriaIfExists(params.getFollowUpBeforeExpression(), query::followUpBeforeExpression);
        addCriteriaIfExists(params.getFollowUpBeforeOrNotExistent(), query::followUpBeforeOrNotExistent);
        addCriteriaIfExists(params.getFollowUpBeforeOrNotExistentExpression(), query::followUpBeforeOrNotExistentExpression);
    }

    private <T> void addCriteriaIfExists(T criteriaValue, Consumer<T> queryCriteria) {
        Optional.ofNullable(criteriaValue).ifPresent(queryCriteria);
    }

    private Map<String, Object> validateAndMergeToTaskVariables(String taskId, Map<String, Object> inputVariables) throws IOException {
        String decision = (String) inputVariables.remove(STATE_VARIABLE_NAME);
        inputVariables = getVariablesRegardingDecision(taskId, inputVariables, decision);
        return inputVariables;
    }

    private Map<String, Object> getVariablesRegardingDecision(String taskId, Map<String, Object> inputVariables, String decision) throws IOException {
        inputVariables = formService.shouldProcessSubmittedData(taskId, decision)
                ? validateAndMergeToTaskVariables(inputVariables, taskId)
                : new HashMap<>();
        inputVariables.put(DECISION_VARIABLE_NAME, decision);
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

    private Map<String, Object> validateAndMergeToTaskVariables(Map<String, Object> inputVariables, String taskId)
            throws IOException {
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
