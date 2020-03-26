package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import de.otto.edison.hal.HalRepresentation;
import de.otto.edison.hal.Link;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import java.io.*;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentSvcTest extends ServiceTest {

    @Mock
    private ProcessApplicationInterface processApplication;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private DeploymentSvc deploymentSvc = new DeploymentSvc();

    @Before
    public void init() throws NoSuchFieldException {
        Field repositoryServiceField = deploymentSvc.getClass().getDeclaredField("repositoryService");
        setField(deploymentSvc, repositoryServiceField, getRepositoryService());
        Field managementServiceField = deploymentSvc.getClass().getDeclaredField("managementService");
        setField(deploymentSvc, managementServiceField, getManagementService());
    }

    @After
    public void tearDown() {
        List<Deployment> deploymentList = getRepositoryService().createDeploymentQuery().list();
        deploymentList.forEach(deployment -> getRepositoryService().deleteDeployment(deployment.getId()));
        ResourceBundle.clearCache(deploymentSvc.getClass().getClassLoader());
    }

    @Test
    public void testCreate() throws IOException, URISyntaxException {
        String deploymentName = "TestDeploymentName";
        String textFilename = "test-file-for-deployment-content.txt";
        String bpmProcessFilename = "simple-test-process.bpmn";
        File textFile = getFile(textFilename);
        File bpmProcessFile = getFile(bpmProcessFilename);

        MultipartFormDataInput formData = mock(MultipartFormDataInput.class);
        InputPart inputTextFile = mock(InputPart.class);
        InputPart inputBpmProcessFile = mock(InputPart.class);
        ProcessApplicationReference processApplicationReference = mock(ProcessApplicationReference.class);
        Map<String, List<InputPart>> paramsMap = new HashMap<>() {{
            put(textFilename, asList(inputTextFile));
            put(bpmProcessFilename, asList(inputBpmProcessFile));
        }};

        when(processApplication.getReference()).thenReturn(processApplicationReference);
        when(formData.getFormDataMap()).thenReturn(paramsMap);
        when(inputTextFile.getBody(InputStream.class, null))
                .thenReturn(new FileInputStream(textFile));
        when(inputBpmProcessFile.getBody(InputStream.class, null))
                .thenReturn(new FileInputStream(bpmProcessFile));

        DeploymentRepresentation actualRepresentation = deploymentSvc.create(deploymentName, formData);

        assertNotNull(actualRepresentation.getId());
        List<ResourceEntity> deploymentResources = getDeploymentResources(actualRepresentation.getId());

        assertTrue(!deploymentResources.isEmpty());
        deploymentResources.forEach(resource -> assertTrue(resource.getBytes().length != 0));
        assertEquals(deploymentName, actualRepresentation.getName());

    }

    @Test
    public void testList_DeploymentsExist() throws IOException, URISyntaxException {
        createDeployment("test-deployment-1", "test-file-for-deployment-content.txt");
        createDeployment("test-deployment-2", "simple-test-process.bpmn");

        List<DeploymentRepresentation> actual = deploymentSvc.list();

        assertTrue(!actual.isEmpty());
        actual.forEach(dto -> assertNotNull(dto.getId()));
    }

    @Test
    public void testList_DeploymentsNotExist() {
        List<DeploymentRepresentation> actual = deploymentSvc.list();

        assertTrue(actual.isEmpty());
    }

    @Test
    public void testDelete_DeploymentWithGivenIdExists() throws IOException, URISyntaxException {
        createDeployment("test-deployment-1", "test-file-for-deployment-content.txt");
        createDeployment("test-deployment-2", "simple-test-process.bpmn");
        String deploymentId = getExistingDeploymentId();

        deploymentSvc.delete(deploymentId);

        assertEquals(1, getDeploymentList().size());
    }

    @Test
    public void testDelete_DeploymentWithGivenIdNotExist() throws IOException, URISyntaxException {
        createDeployment("test-deployment-1", "test-file-for-deployment-content.txt");
        createDeployment("test-deployment-2", "simple-test-process.bpmn");
        String deploymentId = "notExistingId";

        deploymentSvc.delete(deploymentId);

        assertEquals(2, getDeploymentList().size());
    }

    @Test
    public void testExpandZipArchive() {
        InputStream zipIn = Thread.currentThread().getContextClassLoader().getResourceAsStream("compressed-resources.zip");
        
        Map<String, InputStream> actuals = deploymentSvc.expandZipArchive(zipIn);
        
        assertEquals(2, actuals.size());
        assertTrue(actuals.containsKey("first.txt"));
        assertTrue(actuals.containsKey("subfolder/second.txt"));
        assertNotNull(actuals.get("first.txt"));
        assertNotNull(actuals.get("subfolder/second.txt"));
    }
    
    @Test
    public void testExpandIfArchive() throws IOException {
        InputStream zipIn = Thread.currentThread().getContextClassLoader().getResourceAsStream("compressed-resources.zip");
        InputPart zipInputPart = mock(InputPart.class);
        when(zipInputPart.getBody(InputStream.class, null)).thenReturn(zipIn);
        when(zipInputPart.getMediaType()).thenReturn(MediaType.valueOf("application/zip"));
        
        Map<String, InputStream> actuals = deploymentSvc.expandIfArchive("zipped part", zipInputPart);

        assertEquals(2, actuals.size());
        assertTrue(actuals.containsKey("first.txt"));
        assertTrue(actuals.containsKey("subfolder/second.txt"));
        assertNotNull(actuals.get("first.txt"));
        assertNotNull(actuals.get("subfolder/second.txt"));
    }

    @Test
    public void testExpandIfArchive_IfNonArchivePart() throws IOException {
        InputStream stringIn = new ByteArrayInputStream("test input string".getBytes());
        InputPart stringInputPart = mock(InputPart.class);
        when(stringInputPart.getBody(InputStream.class, null)).thenReturn(stringIn);
        when(stringInputPart.getMediaType()).thenReturn(MediaType.valueOf("text/plain"));
        
        Map<String, InputStream> actuals = deploymentSvc.expandIfArchive("string part", stringInputPart);

        assertEquals(1, actuals.size());
        assertNotNull(actuals.get("string part"));
    }
    
    public void testGetFileParts() throws IOException {
        InputStream zipIn = Thread.currentThread().getContextClassLoader().getResourceAsStream("compressed-resources.zip");
        InputPart zipInputPart = mock(InputPart.class);
        InputStream stringIn = new ByteArrayInputStream("test input string".getBytes());
        InputPart stringInputPart = mock(InputPart.class);
        MultipartFormDataInput formData = mock(MultipartFormDataInput.class);
        Map<String, List<InputPart>> paramsMap = new HashMap<String, List<InputPart>>() {{
            put("zip input", asList(zipInputPart));
            put("string input", asList(stringInputPart));
        }};

        when(formData.getFormDataMap()).thenReturn(paramsMap);
        when(zipInputPart.getBody(InputStream.class, null)).thenReturn(zipIn);
        when(zipInputPart.getMediaType()).thenReturn(MediaType.valueOf("application/zip"));
        when(stringInputPart.getBody(InputStream.class, null)).thenReturn(stringIn);
        when(stringInputPart.getMediaType()).thenReturn(MediaType.valueOf("text/plain"));

        Map<String, InputStream> actuals = deploymentSvc.getFileParts(formData);
        
        assertEquals(3, actuals.size());
        assertTrue(actuals.containsKey("first.txt"));
        assertTrue(actuals.containsKey("subfolder/second.txt"));
        assertTrue(actuals.containsKey("string input"));
        assertNotNull(actuals.get("first.txt"));
        assertNotNull(actuals.get("subfolder/second.txt"));
        assertNotNull(actuals.get("string input"));
    }

    private String getExistingDeploymentId() {
        return getDeploymentList().get(0).getId();
    }
    
    @Test
    @org.camunda.bpm.engine.test.Deployment(resources = {"process-with-froms-from-deployment.bpmn", "public/simpleStartForm.json", "public/simpleTaskForm.json"})
    public void testListPublicResources() {
        ProcessDefinition processDefinition = getLastProcessDefinition("processWithFormsFromDeployment");
        String startFormKey = getFormService().getStartFormKey(processDefinition.getId());
        when(httpRequest.getRequestURL()).thenReturn(new StringBuffer("http://localhost:8080/bpms-rest/api/deployment/public-resources"));
        
        HalRepresentation actual = deploymentSvc.listPublicResources(processDefinition.getId(), null, startFormKey);
        
        Link baseUrl = actual.getLinks().getLinkBy("resourcesBaseUrl").get();
        List<String> actualItemNames = actual.getLinks().getLinksBy("items").stream().map(Link::getName).collect(Collectors.toList());
        List<String> actualHrefs = actual.getLinks().getLinksBy("items").stream().map(Link::getHref).collect(Collectors.toList());
        
        assertEquals(String.format("http://localhost:8080/bpms-rest/api/deployment/public-resource/embedded:deployment:/%s/", processDefinition.getDeploymentId()), baseUrl.getHref());
        assertTrue(actualItemNames.contains("simpleStartForm.json"));
        assertTrue(actualHrefs.contains(String.format("http://localhost:8080/bpms-rest/api/deployment/public-resource/embedded:deployment:/%s/simpleStartForm.json", processDefinition.getDeploymentId())));
    }
    
    @Test
    @org.camunda.bpm.engine.test.Deployment(resources = {"process-with-froms-from-deployment.bpmn", "public/simpleStartForm.json"})
    public void testGetPublicResource() throws IOException {
        ProcessDefinition processDefinition = getLastProcessDefinition("processWithFormsFromDeployment");
        
        Response actual = deploymentSvc.getPublicResource("embedded:deployment:", processDefinition.getDeploymentId(), asPathSegments("simpleStartForm.json"));

        assertNotNull(actual);
    }
    
    private List<PathSegment> asPathSegments(String path) {
        return Stream.of(path.split("/"))
            .map(p -> new PathSegment() {

                @Override
                public String getPath() {
                    return p;
                }

                @Override
                public MultivaluedMap<String, String> getMatrixParameters() {
                    return null;
                }
            })
            .collect(Collectors.toList());
    }

}
