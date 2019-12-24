package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.repository.DeploymentRepresentation;
import org.camunda.bpm.application.ProcessApplicationInterface;
import org.camunda.bpm.application.ProcessApplicationReference;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentSvcTest extends ServiceTest {

    @Mock
    private ProcessApplicationInterface processApplication;
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
    public void testCreate() throws IOException {
        String deploymentName = "TestDeploymentName";
        String textFilename = "test-file-for-deployment-content.txt";
        String bpmProcessFilename = "simple-test-process.bpmn";
        File textFile = getFile(textFilename);
        File bpmProcessFile = getFile(bpmProcessFilename);

        MultipartFormDataInput formData = mock(MultipartFormDataInput.class);
        InputPart inputTextFile = mock(InputPart.class);
        InputPart inputBpmProcessFile = mock(InputPart.class);
        ProcessApplicationReference processApplicationReference = mock(ProcessApplicationReference.class);
        Map<String, List<InputPart>> paramsMap = new HashMap<String, List<InputPart>>() {{
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
    public void testList_DeploymentsExist() throws IOException {
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
    public void testDelete_DeploymentWithGivenIdExists() throws IOException {
        createDeployment("test-deployment-1", "test-file-for-deployment-content.txt");
        createDeployment("test-deployment-2", "simple-test-process.bpmn");
        String deploymentId = getExistingDeploymentId();

        deploymentSvc.delete(deploymentId);

        assertEquals(1, getDeploymentList().size());
    }

    @Test
    public void testDelete_DeploymentWithGivenIdNotExist() throws IOException {
        createDeployment("test-deployment-1", "test-file-for-deployment-content.txt");
        createDeployment("test-deployment-2", "simple-test-process.bpmn");
        String deploymentId = "notExistingId";

        deploymentSvc.delete(deploymentId);

        assertEquals(2, getDeploymentList().size());
    }

    @Test
    public void testGetLocalizationResource_CaseDefinitionIdIsNull_OneLanguageRangeIsPassed() throws IOException {
        String baseName = "simple-test-process";
        String resourceName = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        createDeployment("test-deployment", "simple-test-process.bpmn", resourceName);
        String processDefinitionId = getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("myProcess").singleResult().getId();
        String caseDefinitionId = null;
        String languageRangePreferences = "fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR_testv1", "value1");
            put("property_fl_Tscr_TR_testv2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_ProcessDefinitionIdIsNull_OneLanguageRangeIsPassed() throws IOException {
        String baseName = "simple-case-plan";
        String resourceName = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        createDeployment("test-deployment", "simple-case-plan.cmmn", resourceName);
        String caseDefinitionId = getRepositoryService().createCaseDefinitionQuery().caseDefinitionKey("myCasePlan").singleResult().getId();
        String processDefinitionId = null;
        String languageRangePreferences = "fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR_testv1", "value1");
            put("property_fl_Tscr_TR_testv2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_CaseDefinitionIdIsNull_OneLanguageRangeIsPassed_ThereIsNoExactlyMatchingResourceBundle() throws IOException {
        String baseName = "simple-test-process";
        String resourceName1 = "i18n/" + baseName + "_fl.properties";
        String resourceName2 = "i18n/" + baseName + "_fl_Tscr.properties";
        String resourceName3 = "i18n/" + baseName + "_fl_Tscr_TR.properties";
        String resourceName4 = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        createDeployment("test-deployment", "simple-test-process.bpmn", resourceName1, resourceName2, resourceName3, resourceName4);
        String processDefinitionId = getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("myProcess").singleResult().getId();
        String caseDefinitionId = null;
        String languageRangePreferences = "fl-Tscr";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr1", "value1");
            put("property_fl_Tscr2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_ProcessDefinitionIdIsNull_OneLanguageRangeIsPassed_ThereIsNoExactlyMatchingResourceBundle() throws IOException {
        String baseName = "simple-case-plan";
        String resourceName1 = "i18n/" + baseName + "_fl.properties";
        String resourceName2 = "i18n/" + baseName + "_fl_Tscr.properties";
        String resourceName3 = "i18n/" + baseName + "_fl_Tscr_TR.properties";
        String resourceName4 = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        createDeployment("test-deployment", "simple-case-plan.cmmn", resourceName1, resourceName2, resourceName3, resourceName4);
        String caseDefinitionId = getRepositoryService().createCaseDefinitionQuery().caseDefinitionKey("myCasePlan").singleResult().getId();
        String processDefinitionId = null;
        String languageRangePreferences = "fl-Tscr";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr1", "value1");
            put("property_fl_Tscr2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_CaseDefinitionIdIsNull_MultipleLanguageRangesArePassed_ThereAreBundlesForPassedLanguages() throws IOException {
        String baseName = "simple-test-process";
        String resourceName1 = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        String resourceName2 = "i18n/" + baseName + "_sl.properties";
        String resourceName3 = "i18n/" + baseName + "_tl.properties";
        createDeployment("test-deployment", "simple-test-process.bpmn", resourceName1, resourceName2, resourceName3);
        String processDefinitionId = getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("myProcess").singleResult().getId();
        String caseDefinitionId = null;
        String languageRangePreferences = "sl;q=0.5, tl;q=0.1,fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR_testv1", "value1");
            put("property_fl_Tscr_TR_testv2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_ProcessDefinitionIdIsNull_MultipleLanguageRangesArePassed_ThereAreBundlesForPassedLanguages() throws IOException {
        String baseName = "simple-case-plan";
        String resourceName1 = "i18n/" + baseName + "_fl_Tscr_TR_testv.properties";
        String resourceName2 = "i18n/" + baseName + "_sl.properties";
        String resourceName3 = "i18n/" + baseName + "_tl.properties";
        createDeployment("test-deployment", "simple-case-plan.cmmn", resourceName1, resourceName2, resourceName3);
        String caseDefinitionId = getRepositoryService().createCaseDefinitionQuery().caseDefinitionKey("myCasePlan").singleResult().getId();
        String processDefinitionId = null;
        String languageRangePreferences = "sl;q=0.5, tl;q=0.1, fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR_testv1", "value1");
            put("property_fl_Tscr_TR_testv2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_CaseDefinitionIdIsNull_MultipleLanguageRangesArePassed_ThereAreNotAllBundlesMatchingExactlyToPassedLanguages() throws IOException {
        String baseName = "simple-test-process";
        String resourceName1 = "i18n/" + baseName + "_fl_Tscr_TR.properties";
        String resourceName2 = "i18n/" + baseName + "_sl.properties";
        String resourceName3 = "i18n/" + baseName + "_tl.properties";
        createDeployment("test-deployment", "simple-test-process.bpmn", resourceName1, resourceName2, resourceName3);
        String processDefinitionId = getRepositoryService().createProcessDefinitionQuery().processDefinitionKey("myProcess").singleResult().getId();
        String caseDefinitionId = null;
        String languageRangePreferences = "sl;q=0.5, tl;q=0.1, fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR1", "value1");
            put("property_fl_Tscr_TR2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    @Test
    public void testGetLocalizationResource_ProcessDefinitionIdIsNull_MultipleLanguageRangesArePassed_ThereAreNotAllBundlesMatchingExactlyToPassedLanguages() throws IOException {
        String baseName = "simple-case-plan";
        String resourceName1 = "i18n/" + baseName + "_fl_Tscr_TR.properties";
        String resourceName2 = "i18n/" + baseName + "_sl.properties";
        String resourceName3 = "i18n/" + baseName + "_tl.properties";
        createDeployment("test-deployment", "simple-case-plan.cmmn", resourceName1, resourceName2, resourceName3);
        String caseDefinitionId = getRepositoryService().createCaseDefinitionQuery().caseDefinitionKey("myCasePlan").singleResult().getId();
        String processDefinitionId = null;
        String languageRangePreferences = "sl;q=0.5, tl;q=0.1, fl-Tscr-TR-testv";
        Map<String, String> expectedLocalizationResource = new HashMap<String, String>() {{
            put("property_fl_Tscr_TR1", "value1");
            put("property_fl_Tscr_TR2", "value2");
        }};

        Map<String, String> actualLocalizationResource = deploymentSvc.getLocalizationResource(processDefinitionId, caseDefinitionId, languageRangePreferences);

        assertEquals(expectedLocalizationResource, actualLocalizationResource);
    }

    private String getExistingDeploymentId() {
        return getDeploymentList().get(0).getId();
    }

}
