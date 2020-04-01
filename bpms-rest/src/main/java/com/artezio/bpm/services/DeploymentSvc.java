package com.artezio.bpm.services;

import com.artezio.bpm.resources.AbstractResourceLoader;
import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import com.artezio.bpm.services.exceptions.NotFoundException;
import com.artezio.forms.resources.ResourceLoader;
import com.artezio.logging.Log;
import de.otto.edison.hal.HalRepresentation;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.*;
import org.jboss.resteasy.annotations.cache.Cache;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.artezio.logging.Log.Level.CONFIG;
import static de.otto.edison.hal.Link.link;
import static de.otto.edison.hal.Link.linkBuilder;
import static de.otto.edison.hal.Links.linkingTo;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

@Startup
@DependsOn("DefaultEjbProcessApplication")
@Singleton
@Path("/deployment")
public class DeploymentSvc {

    public static final String PUBLIC_RESOURCES_DIRECTORY = "public";
    private static final MediaType MEDIA_TYPE_ZIP = MediaType.valueOf("application/zip");
    private static final int CACHE_MAX_AGE = 31536000;
    private static final Tika CONTENT_ANALYSER = new Tika();

    @Inject
    private RepositoryService repositoryService;
    @Inject
    private ManagementService managementService;
    @Inject
    private ProcessApplicationInterface processApplication;
    @Context
    private HttpServletRequest httpRequest;
    private Logger log = Logger.getLogger(DeploymentSvc.class.getName());

    @PostConstruct
    @Log(level = CONFIG, beforeExecuteMessage = "Registration existent deployments in process application",
            afterExecuteMessage = "All existent deployments are registered in process application")
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
    @Log(beforeExecuteMessage = "Creating deployment '{0}'", afterExecuteMessage = "Deployment '{0}' is created")
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
        getFormParts(input).entrySet()
                .stream()
                .peek(e -> log.info("Register to deploy: " + e.getKey()))
                .forEach(e -> deploymentBuilder.addInputStream(e.getKey(), e.getValue()));
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
    @Log(beforeExecuteMessage = "Deleting deployment '{0}'", afterExecuteMessage = "Deployment '{0}' is deleted")
    public void delete(
            @Parameter(description = "The id of the deployment.", required = true) @PathParam("deployment-id") @NotNull String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    @PermitAll
    @GET
    @Path("/public-resources")
    @Produces("application/hal+json")
    @Operation(
            description = "Get a list of links to public resources in HAL format.",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Request successful")
            }
    )
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of public resources for form '{2}'")
    @Cache(maxAge = CACHE_MAX_AGE, isPrivate = true)
    public HalRepresentation listPublicResources(
            @Parameter(description = "The id of process definition which has the resources. Not required, if 'case-definition-id' is passed.", allowEmptyValue = true) @QueryParam("process-definition-id") String processDefinitionId,
            @Parameter(description = "The id of case definition which has the resources. Not required, if 'process-definition-id' is passed.", allowEmptyValue = true) @QueryParam("case-definition-id") String caseDefinitionId,
            @Parameter(description = "The key of a form for which resources are requested.") @QueryParam("form-key") String formKey) {
        String deploymentId = getResourceDefinition(processDefinitionId, caseDefinitionId).getDeploymentId();
        ResourceLoader resourceLoader = AbstractResourceLoader
                .getResourceLoader(deploymentId, formKey, PUBLIC_RESOURCES_DIRECTORY);
        String deploymentProtocol = AbstractResourceLoader.getProtocol(formKey);
        List<String> resources = resourceLoader.listResourceNames();
        String baseUrl = getBaseUrl();
        return new HalRepresentation(
                linkingTo()
                        .single(link("resourcesBaseUrl",
                                String.format("%s/deployment/public-resource/%s/%s/", baseUrl, deploymentProtocol, deploymentId)))
                        .array(resources
                                .stream()
                                .map(resource -> resource.replaceFirst(PUBLIC_RESOURCES_DIRECTORY + "/", ""))
                                .map(resource -> linkBuilder("items",
                                        String.format("%s/deployment/public-resource/%s/%s/%s", baseUrl, deploymentProtocol, deploymentId, resource))
                                        .withName(resource)
                                        .build())
                                .collect(Collectors.toList()))
                        .build());
    }

    @PermitAll
    @GET
    @Path("/public-resource/{deployment-protocol}/{deployment-id}/{resource-key: .*}")
    @Produces(MediaType.WILDCARD)
    @Log(level = CONFIG, beforeExecuteMessage = "Getting a public resource using protocol '{0}'")
    @Cache(maxAge = CACHE_MAX_AGE, isPrivate = true)
    @Operation(
            description = "Get a public resource in accordance to the protocol",
            externalDocs = @ExternalDocumentation(url = "https://github.com/Artezio/ART-BPMS-REST/blob/master/doc/deployment-service-api-docs.md"),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Request successful"),
                    @ApiResponse(responseCode = "404", description = "Resource is not found")
            }
    )
    public Response getPublicResource(
            @Parameter(description = "Deployment protocol of the requested resource ('embedded:app:' or 'embedded:deployment:').", required = true) @PathParam("deployment-protocol") @Valid @NotNull String deploymentProtocol,
            @Parameter(description = "The id of the deployment connected with requested resource.", required = true) @PathParam("deployment-id") @Valid @NotNull String deploymentId,
            @Parameter(description = "The requested resource path. No deployment protocol is needed.", required = true) @PathParam("resource-key") @Valid @NotNull List<PathSegment> resourceKey) throws IOException {
        ResourceLoader resourceLoader = AbstractResourceLoader
                .getResourceLoader(deploymentId, deploymentProtocol + resourceKey, PUBLIC_RESOURCES_DIRECTORY);
        String resourcePath = resourceKey.stream().map(PathSegment::getPath).collect(Collectors.joining("/"));
        String resourceMimeType = CONTENT_ANALYSER.detect(resourcePath);
        InputStream resource = resourceLoader.getResource(resourcePath);
        if (resource.available() == 0)
            throw new NotFoundException(String.format("Resource %s is not found", resourceKey));
        return Response.ok()
                .entity(resource)
                .type(resourceMimeType)
                .build();
    }

    private void registerInProcessApplication(Deployment deployment) {
        managementService.registerProcessApplication(deployment.getId(), processApplication.getReference());
    }

    private Map<String, InputStream> getFormParts(MultipartFormDataInput input) {
        return getFileParts(input);
    }

    private ResourceDefinition getResourceDefinition(String processDefinitionId, String caseDefinitionId) {
        return processDefinitionId != null
                ? getProcessDefinition(processDefinitionId)
                : getCaseDefinition(caseDefinitionId);
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
    
    Map<String, InputStream> getFileParts(MultipartFormDataInput input) {
         return input.getFormDataMap()
                .entrySet()
                .stream()
                .flatMap(e -> expandIfArchive(e.getKey(), e.getValue().get(0)).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    Map<String, InputStream> expandIfArchive(String partName, InputPart inputPart) {
        try {
            InputStream body = inputPart.getBody(InputStream.class, null);
            return inputPart.getMediaType() != null && inputPart.getMediaType().isCompatible(MEDIA_TYPE_ZIP)
                    ? expandZipArchive(body)
                    : Collections.singletonMap(partName, body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    Map<String, InputStream> expandZipArchive(InputStream zipInput) {
        try {
            Map<String, InputStream> result = new HashMap<>();
            ZipArchiveInputStream zip = new ZipArchiveInputStream(zipInput);
            ZipArchiveEntry zipEntry;
            while ((zipEntry = zip.getNextZipEntry()) != null) {
                if (zipEntry.isDirectory())
                    continue;
                result.put(zipEntry.getName(), new ByteArrayInputStream(IOUtils.toByteArray(zip)));
            }
            zip.close();
            return result;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
        
}
