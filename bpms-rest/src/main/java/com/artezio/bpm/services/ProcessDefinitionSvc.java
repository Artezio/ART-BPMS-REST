package com.artezio.bpm.services;

import com.artezio.bpm.integration.CamundaFileStorage;
import com.artezio.bpm.rest.dto.repository.ProcessDefinitionRepresentation;
import com.artezio.bpm.rest.dto.task.FormDto;
import com.artezio.bpm.rest.dto.task.TaskRepresentation;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.validation.VariableValidator;
import com.artezio.forms.storages.FileStorage;
import com.artezio.logging.Log;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.form.FormData;
import org.camunda.bpm.engine.form.StartFormData;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.IdentityLinkType;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperties;
import org.camunda.bpm.model.bpmn.instance.camunda.CamundaProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.annotation.security.PermitAll;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.artezio.bpm.services.DeploymentSvc.PUBLIC_RESOURCES_DIRECTORY;
import static com.artezio.bpm.services.VariablesMapper.EXTENSION_NAME_PREFIX;
import static com.artezio.logging.Log.Level.CONFIG;
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Transactional
@RestController
@RequestMapping("/api/process-definition")
public class ProcessDefinitionSvc {

    private final IdentitySvc identityService;
    private final FormService camundaFormService;
    private final FormSvc formService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final VariablesMapper variablesMapper;
    private final TaskSvc taskService;
    private final VariableValidator variableValidator;

    @Autowired
    public ProcessDefinitionSvc(IdentitySvc identityService, FormService camundaFormService, FormSvc formService,
                                RuntimeService runtimeService, RepositoryService repositoryService,
                                VariablesMapper variablesMapper, TaskSvc taskService, VariableValidator variableValidator) {
        this.identityService = identityService;
        this.camundaFormService = camundaFormService;
        this.formService = formService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.variablesMapper = variablesMapper;
        this.taskService = taskService;
        this.variableValidator = variableValidator;
    }

    @PermitAll
    @GetMapping(value = "/", produces = APPLICATION_JSON)
    @Operation(
            description = "List process definitions startable by the user created this request.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/process-definition-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successful request.",
                            content = @Content(
                                    mediaType = APPLICATION_JSON,
                                    schema = @Schema(ref = "#/components/schemas/ProcessDefinitionRepresentation")
                            )
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of process definitions startable by the caller")
    public List<ProcessDefinitionRepresentation> listStartableByUser() {
        return repositoryService
                .createProcessDefinitionQuery()
                .latestVersion()
                .list()
                .stream()
                .filter(this::isStartableByUser)
                .map(ProcessDefinitionRepresentation::fromProcessDefinition)
                .collect(Collectors.toList());
    }

    @PostMapping(value = "/key/{process-definition-key}/start", consumes = APPLICATION_JSON, produces = APPLICATION_JSON)
    @PermitAll
    @Operation(
            description = "Instantiate a process definition.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/process-definition-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(
                                    mediaType = APPLICATION_JSON,
                                    schema = @Schema(ref = "#/components/schemas/TaskRepresentation")
                            )
                    ),
                    @ApiResponse(responseCode = "204", description = "Request successful, but there are no tasks assigned to the user."),
                    @ApiResponse(responseCode = "403", description = "The user is not allowed to start the process.")
            }
    )
    @Log(beforeExecuteMessage = "Starting process '{0}'", afterExecuteMessage = "Process '{0}' is started")
    public TaskRepresentation start(
            @Parameter(description = "The key of the process definition to be started.") @PathVariable("process-definition-key") @Valid @NotNull String processDefinitionKey,
            @RequestBody(description = "A JSON object with variables.") Map<String, Object> inputVariables) throws IOException {
        ProcessDefinition processDefinition = getLastProcessDefinition(processDefinitionKey);
        ensureStartableByUser(processDefinition);
        ProcessInstance processInstance = processDefinition.hasStartFormKey()
                ? startProcessByFormSubmission(processDefinition, inputVariables)
                : startProcess(processDefinition, inputVariables);
        return TaskRepresentation.fromEntity(taskService.getNextAssignedTask(processInstance.getProcessInstanceId()));
    }

    @GetMapping(value = "key/{process-definition-key}/rendered-form", produces = APPLICATION_JSON)
    @PermitAll
    @Operation(
            description = "Load the start form definition with data.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/process-definition-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "The user doesn't have an access to load start form for the process."
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "No deployed form for a given process definition exists."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Loading start form for process '{0}'")
    public String loadRenderedStartForm(
            @Parameter(description = "The key of the process definition, which form is loaded for.") @PathVariable("process-definition-key") @Valid @NotNull String processDefinitionKey) throws IOException {
        ProcessDefinition processDefinition = getLastProcessDefinition(processDefinitionKey);
        ensureStartableByUser(processDefinition);
        FormData formData = camundaFormService.getStartFormData(processDefinition.getId());
        Map<String, Object> startFormVariables = getStartFormVariables(formData);
        return formService.getStartFormWithData(processDefinition.getId(), startFormVariables, PUBLIC_RESOURCES_DIRECTORY);
    }

    @GetMapping(value = "key/{process-definition-key}/form", produces = APPLICATION_JSON)
    @PermitAll
    @Operation(
            description = "Retrieves the start form key.",
            externalDocs = @ExternalDocumentation(
                    url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/process-definition-service-api-docs.md"
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Task with given id does not exist."
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Loading start form info for process '{0}'")
    public FormDto loadStartForm( 
            @Parameter(description = "The key of the process definition, which form is loaded for.") @PathVariable("process-definition-key") @Valid @NotNull String processDefinitionKey) throws IOException {
        ProcessDefinition processDefinition = getLastProcessDefinition(processDefinitionKey);
        ensureStartableByUser(processDefinition);
        FormData formData = camundaFormService.getStartFormData(processDefinition.getId());
        return FormDto.fromFormData(formData);
    }
    
    protected boolean isStartableByUser(ProcessDefinition processDefinition) {
        List<IdentityLink> links = repositoryService.getIdentityLinksForProcessDefinition(processDefinition.getId());
        return userHasAccess(links);
    }

    protected boolean userHasAccess(List<IdentityLink> links) {
        return links.isEmpty() 
                || userIsInCandidateGroup(links)
                || userIsCandidate(links);
    }

    protected boolean userIsCandidate(List<IdentityLink> links) {
        return links.stream()
                .filter(identityLink -> identityLink.getUserId() != null)
                .filter(identityLink -> identityLink.getUserId().equals(identityService.userId()))
                .anyMatch(identityLink -> identityLink.getType().equals(IdentityLinkType.CANDIDATE));
    }

    protected boolean userIsInCandidateGroup(List<IdentityLink> links) {
        return links.stream()
                .filter(identityLink -> identityLink.getGroupId() != null)
                .filter(identityLink -> identityLink.getType().equals(IdentityLinkType.CANDIDATE))
                .map(IdentityLink::getGroupId)
                .anyMatch(role -> identityService.userGroups().contains(role));
    }

    protected Map<String, Object> validateAndMergeToFormVariables(Map<String, Object> inputVariables,
                                                                  String processDefinitionId) throws IOException {
        StartFormData formData = camundaFormService.getStartFormData(processDefinitionId);
        String formKey = formData.getFormKey();
        if (formKey == null) {
            throw new RuntimeException("Process has no start form");
        } else {
            Map<String, Object> formVariables = getStartFormVariables(formData);
            FileStorage fileStorage = new CamundaFileStorage(formVariables);
            String validatedVariablesJson = formService.dryValidationAndCleanupStartForm(processDefinitionId, inputVariables,
                    PUBLIC_RESOURCES_DIRECTORY, fileStorage);
            variablesMapper.updateVariables(formVariables, validatedVariablesJson);
            return formVariables;
        }
    }

    protected void ensureStartableByUser(ProcessDefinition processDefinition) {
        if (!isStartableByUser(processDefinition)) {
            throw new NotAuthorizedException();
        }
    }

    protected Map<String, Object> getStartFormVariables(FormData formData) {
        return formData
                .getFormFields()
                .stream()
                .collect(HashMap::new,
                        (map, formField) -> map.put(formField.getId(), formField.getValue().getValue()),
                        HashMap::putAll);
    }

    protected ProcessDefinition getLastProcessDefinition(String processDefinitionKey) {
        return repositoryService.createProcessDefinitionQuery()
                .latestVersion()
                .processDefinitionKey(processDefinitionKey)
                .singleResult();
    }

    private Map<String, String> getProcessExtensions(ProcessDefinition processDefinition) {
        String processDefinitionId = processDefinition.getId();
        String processDefinitionKey = processDefinition.getKey();
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

    private ProcessInstance startProcess(ProcessDefinition processDefinition, Map<String, Object> inputVariables) {
        Map<String, String> processExtensions = getProcessExtensions(processDefinition);
        inputVariables = variablesMapper.convertVariablesToEntities(inputVariables, processExtensions);
        variableValidator.validate(inputVariables);
        return runtimeService.startProcessInstanceByKey(processDefinition.getKey(), inputVariables);
    }

    private ProcessInstance startProcessByFormSubmission(ProcessDefinition processDefinition, Map<String, Object> variables) throws IOException {
        Map<String, Object> mergedVariables = validateAndMergeToFormVariables(variables, processDefinition.getId());
        Map<String, String> processExtensions = getProcessExtensions(processDefinition);
        mergedVariables = variablesMapper.convertVariablesToEntities(mergedVariables, processExtensions);
        variableValidator.validate(mergedVariables);
        return camundaFormService.submitStartForm(processDefinition.getId(), mergedVariables);
    }

}
