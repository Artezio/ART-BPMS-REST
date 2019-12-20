package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.ServiceTest;
import com.artezio.bpm.services.VariablesMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FormioClientTest extends ServiceTest {

    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "dryValidationAndCleanUp.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";

    @Mock
    private DeploymentSvc deploymentSvc;
    @Mock
    private VariablesMapper variablesMapper;
    @Mock
    private NodeJsProcessor nodeJsProcessor;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();
    private ObjectMapper objectMapper = new ObjectMapper();
    {
        System.setProperty("NODE_MODULES_PATH", System.getProperty("java.io.tmpdir"));
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field formsCacheField = FormioClient.class.getDeclaredField("FORM_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormioClient.class)).clear();
    }

    @Test
    public void testGetFormWithData_NoDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
           put("data", variables);
        }};

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(variablesMapper.convertEntitiesToMaps(variables)).thenReturn(variables);
                when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.getFormWithData(deploymentId, formPath, variables);

                assertEquals(expected.toString(), actual);
            }
        }
    }

    @Test
    public void testGetFormWithData_ExistentDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("testFile", "fileContent");
        }};
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", variables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.valueToTree(variables));
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(variablesMapper.convertEntitiesToMaps(variables)).thenReturn(variables);
                when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.getFormWithData(deploymentId, formPath, variables);

                assertEquals(expected.toString(), actual);
            }
        }
    }

    @Test
    public void testGetFormWithData_NonexistentDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("testFile", "fileContent");
        }};
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", variables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(variablesMapper.convertEntitiesToMaps(variables)).thenReturn(variables);
                when(nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.getFormWithData(deploymentId, formPath, variables);

                assertEquals(expected.toString(), actual);
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_NoDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", variables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, variables);

                assertEquals(objectMapper.writeValueAsString(variables), actual);
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/test.json";
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("text", "123");
        }};
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", variables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.valueToTree(variables));
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, variables);

                assertEquals(objectMapper.writeValueAsString(variables), actual);
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_InvalidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", variables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, variables);

                assertEquals(objectMapper.writeValueAsString(variables), actual);
            }
        }
    }

    @Test
    public void testUnwrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode submittedData = new ObjectMapper().readTree(
                "{" +
                        "\"text\": \"text\", " +
                        "\"nested-1\": {" +
                        "   \"metadata\": {}, " +
                        "   \"data\": {" +
                        "       \"nested-1-text\": \"text1\"," +
                        "       \"nested-2\": {" +
                        "           \"metadata\": {}," +
                        "           \"data\": {" +
                        "               \"nested-2-text\": \"text2\"" +
                        "           }" +
                        "       }" +
                        "   }" +
                        "}," +
                        "\"multipleForms\": [" +
                        "   {\"nested-array-form\": {" +
                        "       \"metadata\": \"\"," +
                        "       \"data\": {}" +
                        "   }" +
                        "}]}");
        JsonNode expectedData = new ObjectMapper().readTree(
                "{" +
                        "   \"text\": \"text\", " +
                        "   \"nested-1\": {" +
                        "       \"nested-1-text\": \"text1\"," +
                        "       \"nested-2\": {" +
                        "           \"nested-2-text\": \"text2\"" +
                        "       }" +
                        "   }," +
                        "   \"multipleForms\": [{}]" +
                        "}");
        JsonNode actual = formioClient.unwrapSubformData(submittedData, definition);
        assertEquals(expectedData, actual);
    }

    @Test
    public void testWrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode sourceData = new ObjectMapper().readTree(
                "{" +
                        "   \"text\": \"text\", " +
                        "   \"nested-1\": {" +
                        "       \"nested-1-text\": \"text1\"," +
                        "       \"nested-2\": {" +
                        "           \"nested-2-text\": \"text2\"" +
                        "       }" +
                        "   }," +
                        "   \"multipleForms\": [" +
                        "       {" +
                        "           \"nested-array-form\": {}" +
                        "       }" +
                        "   ]" +
                        "}");
        JsonNode actual = formioClient.wrapSubformData(sourceData, definition);
        assertFalse(actual.at("/data").isMissingNode());
        assertFalse(actual.at("/data/nested-1/data").isMissingNode());
        assertFalse(actual.at("/data/nested-1/data/nested-2/data").isMissingNode());
        assertEquals("text2", actual.at("/data/nested-1/data/nested-2/data/nested-2-text").asText());
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsSubmitted() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "submitted";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmittedData(deploymentId, formPath, submissionState);

        assertTrue(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "canceled";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmittedData(deploymentId, formPath, submissionState);

        assertFalse(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "submittedWithoutProperty";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmittedData(deploymentId, formPath, submissionState);

        assertTrue(actual);
    }

}
