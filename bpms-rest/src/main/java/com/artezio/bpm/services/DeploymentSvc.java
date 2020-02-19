package com.artezio.bpm.services;

import com.artezio.bpm.localization.BpmResourceBundleControl;
import com.artezio.bpm.resources.AbstractResourceLoader;
import com.artezio.bpm.resources.AppResourceLoader;
import com.artezio.bpm.resources.DeploymentResourceLoader;
import com.artezio.bpm.resources.ResourceLoader;
import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import com.artezio.logging.Log;

import de.otto.edison.hal.HalRepresentation;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.io.FilenameUtils;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.*;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.artezio.logging.Log.Level.CONFIG;
import static de.otto.edison.hal.Link.linkBuilder;
import static de.otto.edison.hal.Links.linkingTo;
import static javax.ws.rs.core.HttpHeaders.ACCEPT_LANGUAGE;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Startup
@DependsOn("DefaultEjbProcessApplication")
@Singleton
@Path("/deployment")
public class DeploymentSvc {

    private static final Map<String, ResourceBundle> RESOURCE_BUNDLE_CACHE = new ConcurrentHashMap<>();
    private final static Pattern COMPONENT_NAME_PATTERN = Pattern.compile("(?:\\w*:)?.*/(.*)(?:\\.[^\\.]*)$");

    @Inject
    private RepositoryService repositoryService;
    @Inject
    private ManagementService managementService;
    @Inject
    private ProcessApplicationInterface processApplication;
    @Context
    private HttpServletRequest httpRequest;
    @Inject
    private ServletContext servletContext;

    @PostConstruct
    public void registerDeployments() {
        repositoryService.createDeploymentQuery().list()
                .forEach(this::registerInProcessApplication);
    }

    @RolesAllowed("BPMSAdmin")
    @POST
    @Path("/create")
    @Consumes(MULTIPART_FORM_DATA)
    @Produces(APPLICATION_JSON)
    @Operation(
            description = "Create a deployment with specified resources.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(ref = "#/components/schemas/DeploymentRepresentation"))
                    ),
                    @ApiResponse(responseCode = "403", description = "The user is not allowed to create deployments.")
            }
    )
    @Log(beforeExecuteMessage = "Creating deployment '{0}'", afterExecuteMessage = "Deployment '{0}' is successfully created")
    public DeploymentRepresentation create(
            @Parameter(description = "Name for the deployment", required = true) @QueryParam("deployment-name") @Valid @NotNull String deploymentName,
            @Parameter(
                    description = "Resources which the deployment will consist of",
                    required = true,
                    allowEmptyValue = true,
                    content = @Content(mediaType = MULTIPART_FORM_DATA)) @Valid @NotNull MultipartFormDataInput input) {
        DeploymentBuilder deploymentBuilder = repositoryService
                .createDeployment()
                .name(deploymentName);
        getFormParts(input)
                .forEach(deploymentBuilder::addInputStream);
        Deployment deployment = deploymentBuilder.deploy();
        registerInProcessApplication(deployment);
        return DeploymentRepresentation.fromDeployment(deployment);
    }

    @RolesAllowed("BPMSAdmin")
    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @Operation(
            description = "Get a list of all deployments.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(mediaType = APPLICATION_JSON, schema = @Schema(ref = "#/components/schemas/DeploymentRepresentation"))
                    )
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of deployments")
    public List<DeploymentRepresentation> list() {
        return repositoryService
                .createDeploymentQuery()
                .list()
                .stream()
                .map(DeploymentRepresentation::fromDeployment)
                .collect(Collectors.toList());
    }

    @PermitAll
    @GET
    @Path("/localization-resource")
    @Produces(APPLICATION_JSON)
    @Operation(
            description = "Get localization resources in accordance with user preferences.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Request successful.",
                            content = @Content(mediaType = APPLICATION_JSON)
                    )
            }
    )
    public Map<String, String> getLocalizationResource(
            @Parameter(description = "The id of process definition which has the resources. Not required, if 'case-definition-id' is passed.", allowEmptyValue = true) @QueryParam("process-definition-id") String processDefinitionId,
            @Parameter(description = "The id of case definition which has the resources. Not required, if 'process-definition-id' is passed.", allowEmptyValue = true) @QueryParam("case-definition-id") String caseDefinitionId,
            @Parameter(
                    description = "User preferences of languages",
                    example = "ru,en;q=0.9,en-US;q=0.8") @NotNull @HeaderParam(ACCEPT_LANGUAGE) String languageRangePreferences) {
        String[] preferredLanguageRanges = languageRangePreferences.replace(" ", "").split(",");
        ResourceDefinition resourceDefinition = getResourceDefinition(processDefinitionId, caseDefinitionId);

        ResourceBundle resourceBundle = Arrays.stream(preferredLanguageRanges)
                .sorted(getLanguageRangeComparator())
                .map(languageRange -> getResourceBundle(resourceDefinition, languageRange))
                .findFirst()
                .get();

        return toMap(resourceBundle);
    }

    @RolesAllowed("BPMSAdmin")
    @DELETE
    @Path("/{deployment-id}")
    @Operation(
            description = "Delete the deployment with specified id.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(responseCode = "204", description = "Request successful"),
                    @ApiResponse(responseCode = "403", description = "The user is not allowed to delete deployments.")
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Deleting deployment '{0}'", afterExecuteMessage = "Deployment is successfully deleted")
    public void delete(
            @Parameter(description = "The id of the deployment.", required = true) @PathParam("deployment-id") @NotNull String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @PermitAll
    @GET
    @Path("/public-resources")
    @Produces("application/hal+json")
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of task resources")
    //TODO document it
    public HalRepresentation listPublicResources(
            @Parameter(description = "The id of process definition which has the resources. Not required, if 'case-definition-id' is passed.", allowEmptyValue = true) @QueryParam("process-definition-id") String processDefinitionId,
            @Parameter(description = "The id of case definition which has the resources. Not required, if 'process-definition-id' is passed.", allowEmptyValue = true) @QueryParam("case-definition-id") String caseDefinitionId,
            @Parameter(description = "The key of a form for which resources are requested.", allowEmptyValue = false) @QueryParam("form-key") String resourceKey) {
        String deploymentId =  getResourceDefinition(processDefinitionId, caseDefinitionId).getDeploymentId();
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, resourceKey);
        List<String> resources = resourceLoader.listResourceNames("");
        String baseUrl = getBaseUrl();
        return new HalRepresentation(
                linkingTo()
                        .array(resources
                                .stream()
                                .map(resource -> linkBuilder("items",
                                        baseUrl + "/deployment/public-resource/"  + deploymentId + "/"+ encodeUrl(resource))
                                        .withRel(resource)
                                        .build())
                                .collect(Collectors.toList()))
                        .build());
    }

    @PermitAll
    @GET
    @Path("/public-resource/{deployment-id}/{resource-key}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of task resources")
    //TODO document it
    public InputStream getPublicResource(
            @Parameter(description = "The id of the deployment connected with resource requested.", required = true) @PathParam("deployment-id") @Valid @NotNull String deploymentId,
            @Parameter(description = "The requested resource path.", required = true) @PathParam("resource-key") @Valid @NotNull String resourceKey)
            throws UnsupportedEncodingException {
        ResourceLoader resourceLoader = getResourceLoader(deploymentId, resourceKey);
        return resourceLoader.getResource(URLDecoder.decode(resourceKey, "UTF-8"));
    }

    private ResourceLoader getResourceLoader(String deploymentId, String resourceKey) {
        return AbstractResourceLoader.getProtocol(resourceKey).equals(AbstractResourceLoader.DEPLOYMENT_PROTOCOL)
                ? new DeploymentResourceLoader(deploymentId)
                : new AppResourceLoader(servletContext);
    }

    private void registerInProcessApplication(Deployment deployment) {
        managementService.registerProcessApplication(deployment.getId(), processApplication.getReference());
    }

    private Map<String, InputStream> getFormParts(MultipartFormDataInput input) {
        return input.getFormDataMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    try {
                        return entry.getValue().get(0).getBody(InputStream.class, null);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }));
    }

    private ResourceBundle getResourceBundle(ResourceDefinition resourceDefinition, String languageRange) {
        String deploymentId = resourceDefinition.getDeploymentId();
        String diagramResourceName = FilenameUtils.getBaseName(resourceDefinition.getResourceName());
        String languageTag = extractLanguageTag(languageRange);
        String resourceBundleCacheKey = String.format("%s.%s.%s", deploymentId, diagramResourceName, languageTag);

        return RESOURCE_BUNDLE_CACHE.computeIfAbsent(resourceBundleCacheKey, cacheKey ->
                ResourceBundle.getBundle(
                        diagramResourceName,
                        Locale.forLanguageTag(languageTag),
                        new BpmResourceBundleControl(deploymentId, repositoryService)));
    }

    private Comparator<String> getLanguageRangeComparator() {
        return Comparator.comparing(
                str -> (str.contains(";q=") ? str : "1").replaceAll("[\\D&&[^.]]", ""),
                Comparator.comparing((Function<String, Double>) Double::valueOf).reversed());
    }

    private String extractLanguageTag(String languageRange) {
        return languageRange.split(";")[0];
    }

    private ResourceDefinition getResourceDefinition(String processDefinitionId, String caseDefinitionId) {
        return processDefinitionId != null
                ? getProcessDefinition(processDefinitionId)
                : getCaseDefinition(caseDefinitionId);
    }

    private Map<String, String> toMap(ResourceBundle resourceBundle) {
        return resourceBundle.keySet().stream()
                .collect(Collectors.toMap(propKey -> propKey, resourceBundle::getString));
    }

    private CaseDefinition getCaseDefinition(String caseDefinitionId) {
        return repositoryService.createCaseDefinitionQuery()
                .caseDefinitionId(caseDefinitionId)
                .singleResult();
    }

    private ProcessDefinition getProcessDefinition(String processDefinitionId) {
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
    }

    private String getBaseUrl() {
        StringBuffer requestUrl = httpRequest.getRequestURL();
        return requestUrl.toString().replaceFirst("/deployment.*", "");
    }
    
    private String encodeUrl(String unsafeUrl) {
        try {
            return URLEncoder.encode(unsafeUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
