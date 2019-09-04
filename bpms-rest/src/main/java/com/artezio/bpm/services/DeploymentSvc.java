package com.artezio.bpm.services;

import com.artezio.bpm.localization.BpmResourceBundleControl;
import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import com.artezio.bpm.startup.FormDeployer;
import com.artezio.logging.Log;
import com.google.common.base.Charsets;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RepositoryService;
import org.camunda.bpm.engine.repository.*;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.artezio.logging.Log.Level.CONFIG;
import static javax.ws.rs.core.HttpHeaders.ACCEPT_LANGUAGE;

@Stateless
@Path("/deployment")
public class DeploymentSvc {

    private static final String FORMS_FOLDER = "forms/";
    private static final Map<String, ResourceBundle> RESOURCE_BUNDLE_CACHE = new ConcurrentHashMap<>();
    @Inject
    private RepositoryService repositoryService;
    @Inject
    private FormDeployer formDeployer;

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
        formDeployer.uploadForms();
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
        try (InputStream in = repositoryService.getResourceAsStream(latestDeployment.getId(), formId)) {
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

    @PermitAll
    @GET
    @Path("/localization-resource")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> getLocalizationResource(
            @QueryParam("process-definition-id") String processDefinitionId,
            @QueryParam("case-definition-id") String caseDefinitionId,
            @NotNull @HeaderParam(ACCEPT_LANGUAGE) String languageRangePreferences) {
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

}
