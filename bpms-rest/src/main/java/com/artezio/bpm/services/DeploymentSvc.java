package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import com.artezio.logging.Log;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.repository.Resource;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.artezio.logging.Log.Level.CONFIG;

@Stateless
@Path("/deployment")
public class DeploymentSvc {

    private static final String FORMS_FOLDER = "forms/";
    @Inject
    private RepositoryService repositoryService;

    @RolesAllowed("BPMSAdmin")
    @POST
    @Path("/create")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @Log(beforeExecuteMessage = "Creating deployment '{0}'", afterExecuteMessage = "Deployment '{0}' is successfully created")
    public DeploymentRepresentation create(
            @QueryParam("deployment-name") @Valid @NotNull String deploymentName,
            @Valid @NotNull MultipartFormDataInput input) {
        DeploymentBuilder deploymentBuilder = repositoryService
                .createDeployment()
                .name(deploymentName);
        getFormParts(input).entrySet()
                .stream()
                .forEach(entry -> deploymentBuilder.addInputStream(entry.getKey(), entry.getValue()));
        Deployment deployment = deploymentBuilder.deploy();
        return DeploymentRepresentation.fromDeployment(deployment);
    }

    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of form ids from deployment resources")
    public List<String> listFormIds() {
        Deployment latestDeployment = getLatestDeployment();
        return repositoryService.getDeploymentResources(latestDeployment.getId()).stream()
                .filter(resource -> resource.getName().startsWith(FORMS_FOLDER))
                .map(Resource::getName)
                .collect(Collectors.toList());
    }

    @PermitAll
    @Log(level = CONFIG, beforeExecuteMessage = "Getting form '{0}' from deployment resources")
    public String getForm(String formId) {
        Deployment latestDeployment = getLatestDeployment();
        formId = !formId.endsWith(".json") ? formId.concat(".json") : formId;
        try(InputStream in = repositoryService.getResourceAsStream(latestDeployment.getId(), formId)){
            return IOUtils.toString(in, Charsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error on load form " + formId, e);
        }
    }

    @PermitAll
    public String getLatestDeploymentId() {
        return getLatestDeployment().getId();
    }

    @RolesAllowed("BPMSAdmin")
    @GET
    @Path("/")
    @Produces(MediaType.APPLICATION_JSON)
    @Log(level = CONFIG, beforeExecuteMessage = "Getting list of deployments")
    public List<DeploymentRepresentation> list() {
        List<DeploymentRepresentation> result = repositoryService
                .createDeploymentQuery()
                .list()
                .stream()
                .map(DeploymentRepresentation::fromDeployment)
                .collect(Collectors.toList());
        return result;
    }

    @RolesAllowed("BPMSAdmin")
    @DELETE
    @Path("/{deployment-id}")
    @Log(level = CONFIG, beforeExecuteMessage = "Deleting deployment '{0}'", afterExecuteMessage = "Deployment is successfully deleted")
    public void delete(@PathParam("deployment-id") @NotNull String deploymentId) {
        repositoryService.deleteDeployment(deploymentId, true);
    }

    private Deployment getLatestDeployment() {
        return repositoryService.createDeploymentQuery()
                .orderByDeploymentTime()
                .desc()
                .list()
                .get(0);
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

}
