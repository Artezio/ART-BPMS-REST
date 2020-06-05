package com.artezio.bpm.services;

import com.artezio.bpm.integration.CamundaFileStorage;
import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.rest.query.task.TaskQueryParams;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.forms.storages.FileStorage;
import com.artezio.forms.storages.FileStorageEntity;
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
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.runtime.CaseExecution;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.task.TaskQuery;
import org.camunda.bpm.engine.variable.VariableMap;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.artezio.bpm.services.DeploymentSvc.PUBLIC_RESOURCES_DIRECTORY;
import static com.artezio.bpm.services.VariablesMapper.EXTENSION_NAME_PREFIX;
import static com.artezio.logging.Log.Level.CONFIG;
import static io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY;
import static java.util.Arrays.copyOfRange;
import static java.util.Collections.emptyMap;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/task")
@Transactional
public class TaskSvc {

    private static final String DECISION_VARIABLE_NAME = "decision";
    private static final String STATE_VARIABLE_NAME = "state";

    private final TaskService taskService;
    private final IdentitySvc identityService;
    private final FormService camundaFormService;
    private final FormSvc formService;
    private final VariablesMapper variablesMapper;
    private final CaseService caseService;
    private final RepositoryService repositoryService;
    private final VariableValidator variableValidator;

    @Autowired
    public TaskSvc(TaskService taskService, IdentitySvc identityService, FormService camundaFormService,
                   FormSvc formService, VariablesMapper variablesMapper, CaseService caseService,
                   RepositoryService repositoryService, VariableValidator variableValidator) {
        this.taskService = taskService;
        this.identityService = identityService;
        this.camundaFormService = camundaFormService;
        this.formService = formService;
        this.variablesMapper = variablesMapper;
        this.caseService = caseService;
        this.repositoryService = repositoryService;
        this.variableValidator = variableValidator;
    }

    @GetMapping(value = "/available", produces = APPLICATION_JSON_VALUE)
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
                                    mediaType = APPLICATION_JSON_VALUE,
                                    array = @ArraySchema(schema = @Schema(implementation = TaskRepresentation.class))
                            )
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of available tasks")
    public @ApiResponse List<TaskRepresentation> listAvailable(
            @org.springframework.web.bind.annotation.RequestBody TaskQueryParams queryParams) {
        TaskQuery taskQuery = createTaskQuery(queryParams)
                .or()
                .taskCandidateGroupIn(identityService.userGroups())
                .taskCandidateUser(identityService.userId())
                .endOr()
                .initializeFormKeys();

        return taskQuery
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/assigned", produces = APPLICATION_JSON_VALUE)
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
                                    mediaType = APPLICATION_JSON_VALUE,
                                    array = @ArraySchema(schema = @Schema(implementation = TaskRepresentation.class))
                            )
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of assigned task")
    public List<TaskRepresentation> listAssigned(@org.springframework.web.bind.annotation.RequestBody TaskQueryParams queryParams) {
        TaskQuery taskQuery = createTaskQuery(queryParams)
                .taskAssignee(identityService.userId())
                .initializeFormKeys();

        return taskQuery
                .list()
                .stream()
                .map(TaskRepresentation::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping(value = "/{task-id}/claim")
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
    @Log(beforeExecuteMessage = "Claiming task '{0}'", afterExecuteMessage = "Task '{0}' is claimed")
    public void claim(
            @Parameter(description = "The id of the task which is going to be assigned", required = true) @PathVariable(value = "task-id") @Valid @NotNull String taskId) {
        ensureUserHasAccess(taskId);
        taskService.claim(taskId, identityService.userId());
    }

    @GetMapping(value = "/{task-id}/form", produces = APPLICATION_JSON_VALUE)
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
                            content = @Content(mediaType = APPLICATION_JSON_VALUE)
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "A user doesn't have an access to load a form for the task with specified id."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Loading form for user task '{0}'")
    public String loadForm(
            @Parameter(description = "The id of the task which form is requested for.", required = true) @PathVariable("task-id") @Valid @NotNull String taskId) {
        ensureUserHasAccess(taskId);
        List<String> formFieldsNames = formService.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY);
        VariableMap taskVariables = taskService.getVariablesTyped(taskId, formFieldsNames, true);
        return formService.getTaskFormWithData(taskId, taskVariables, PUBLIC_RESOURCES_DIRECTORY);
    }

    @GetMapping(value = "/{task-id}/file/{file-id}")
    @PermitAll
    @Operation(
            description = "Download a file existing in a scope of the task.",
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
    @Log(level = CONFIG, beforeExecuteMessage = "Downloading file '{1}'", afterExecuteMessage = "File '{1}' is downloaded")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "An id of the task which has in its scope requested file as variable.", required = true) @PathVariable("task-id") @Valid @NotNull String taskId,
            @Parameter(description = "Id of requested file.", required = true) @PathVariable("file-id") @Valid @NotNull String fileId) {
        ensureUserHasAccess(taskId);
        checkIfFileAvailable(taskId, fileId);
        FileStorageEntity fileStorageEntity = new CamundaFileStorage(taskId).retrieve(fileId);
        return ResponseEntity
                .ok()
                .contentType(MediaType.valueOf(fileStorageEntity.getMimeType()))
                .header("Content-Disposition", "attachment; filename=" + fileStorageEntity.getName())
                .body(new InputStreamResource(fileStorageEntity.getContent()));
    }

    @PostMapping(value = "/{task-id}/complete", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
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
                                    mediaType = APPLICATION_JSON_VALUE,
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
    @Log(beforeExecuteMessage = "Completing task '{0}'", afterExecuteMessage = "Task '{0}' is completed")
    public TaskRepresentation complete(
            @Parameter(description = "The id of the task to be completed.", required = true) @PathVariable("task-id") @Valid @NotNull String taskId,
            @RequestBody(description = "The variables which will be passed to a process after completing the task.") Map<String, Object> inputVariables)
            throws IOException {
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
    @Log(level = CONFIG, beforeExecuteMessage = "Getting next assigned task for process instance '{0}'")
    public Task getNextAssignedTask(String processInstanceId) {
        List<Task> assignedTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskAssignee(identityService.userId())
                .initializeFormKeys()
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
                .initializeFormKeys()
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
        inputVariables = formService.shouldProcessSubmittedData(taskId, decision, PUBLIC_RESOURCES_DIRECTORY)
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

    private String cleanUpVariableName(String variableName) {
        return variableName.replaceAll("\\W", "");
    }

    private Map<String, Object> validateAndMergeToTaskVariables(Map<String, Object> inputVariables, String taskId)
            throws IOException {
        String formKey = camundaFormService.getTaskFormData(taskId).getFormKey();
        if (formKey != null) {
            List<String> formFieldsNames = formService.getRootTaskFormFieldNames(taskId, PUBLIC_RESOURCES_DIRECTORY);
            Map<String, Object> taskVariables = taskService.getVariables(taskId, formFieldsNames);
            FileStorage fileStorage = new CamundaFileStorage(taskVariables);
            String validatedVariablesJson = formService.dryValidationAndCleanupTaskForm(taskId, inputVariables,
                    taskVariables, PUBLIC_RESOURCES_DIRECTORY, fileStorage);
            variablesMapper.updateVariables(taskVariables, validatedVariablesJson);
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

    private void checkIfFileAvailable(String taskId, String fileId) {
        List<String> taskFormVariableNames = formService.getTaskFormFieldPaths(taskId, PUBLIC_RESOURCES_DIRECTORY);
        fileId = fileId.replaceAll("\\[\\d+]", "");
        if (!taskFormVariableNames.contains(fileId))
            throw new NotFoundException(String.format("No available file with id %s is found", fileId));
    }

}
