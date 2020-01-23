package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.ServiceTest;
import com.artezio.bpm.services.VariablesMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

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

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field formsCacheField = FormioClient.class.getDeclaredField("FORM_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMISSION_PROCESSING_DECISIONS_CACHE");
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
        Map<String, Object> submittedVariables = new HashMap<>();
        Map<String, Object> currentVariables = new HashMap<>();
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", currentVariables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, submittedVariables, currentVariables);

                assertEquals(objectMapper.writeValueAsString(currentVariables), actual);
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/test.json";
        Map<String, Object> currentVariables = new HashMap<>();
        Map<String, Object> submittedVariables = new HashMap<String, Object>() {{
            put("text", "123");
        }};
        Map<String, Object> submissionData = new HashMap<String, Object>() {{
            put("data", submittedVariables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.valueToTree(submittedVariables));
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submissionData))).thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, submittedVariables, currentVariables);

                assertEquals(objectMapper.writeValueAsString(submittedVariables), actual);
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_ValidDataWithChangedReadOnlyVariablePassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/test.json";
        Map<String, Object> currentVariables = new HashMap<String, Object>() {{
            put("readOnly", "test");
        }};
        Map<String, Object> submittedVariables = new HashMap<String, Object>() {{
            put("text", "123");
            put("readOnly", "test2");
        }};
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("text", "123");
            put("readOnly", "test");
        }};
        Map<String, Object> formVariables = new HashMap<String, Object>() {{
            put("data", new HashMap<String, Object>() {{
                put("text", "123");
                put("readOnly", "test");
            }});
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            ObjectNode scriptResult = objectMapper.createObjectNode();
            scriptResult.set("data", objectMapper.valueToTree(expected));
            try (InputStream scriptResultAsStream = IOUtils.toInputStream(scriptResult.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(formVariables))).thenReturn(scriptResultAsStream);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, submittedVariables, currentVariables);

                assertEquals(expected, objectMapper.treeToValue(objectMapper.readTree(actual), Map.class));
            }
        }
    }

    @Test
    public void dryValidationAndCleanupTest_InvalidDataPassed() throws IOException {
        String deploymentId = "deploymentId";
        String formPath = "forms/testForm.json";
        Map<String, Object> currentVariables = new HashMap<>();
        Map<String, Object> submittedVariables = new HashMap<>();
        Map<String, Object> submittedVariablesInFormioView = new HashMap<String, Object>() {{
            put("data", submittedVariables);
        }};
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(formPath);
             InputStream deploymentResource = getClass().getClassLoader().getResourceAsStream(formPath)) {
            JsonNode formDefinition = objectMapper.readTree(is);
            JsonNode expected = formDefinition.deepCopy();
            ((ObjectNode) expected).set("data", objectMapper.createObjectNode());
            try (InputStream scriptResult = IOUtils.toInputStream(expected.toString(), StandardCharsets.UTF_8)) {
                when(deploymentSvc.getResource(deploymentId, formPath)).thenReturn(deploymentResource);
                when(nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition.toString(), objectMapper.writeValueAsString(submittedVariablesInFormioView)))
                        .thenReturn(scriptResult);

                String actual = formioClient.dryValidationAndCleanup(deploymentId, formPath, submittedVariables,currentVariables);

                assertEquals(objectMapper.writeValueAsString(submittedVariables), actual);
            }
        }
    }

    @Test
    public void testUnwrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode submittedData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-submitted-unwrap.json"))));
        JsonNode expectedData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-expected-unwrap.json"))));
        JsonNode actual = formioClient.unwrapGridData(submittedData, definition);
        assertEquals(expectedData, actual);
    }

    @Test
    public void testWrapData() throws IOException {
        JsonNode definition = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms.json"))));
        JsonNode sourceData = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/full-form-with-nested-forms-data-submitted-wrap.json"))));
        JsonNode actual = formioClient.wrapGridData(sourceData, definition);
        assertFalse(actual.at("/nested-1/nested-3-datagrid/0/container").isMissingNode());
        assertEquals("text2", actual.at("/nested-1/nested-2/nested-2-text").asText());
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsSubmitted() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "submitted";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(deploymentId, formPath, submissionState);

        assertTrue(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "canceled";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(deploymentId, formPath, submissionState);

        assertFalse(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException, URISyntaxException {
        String deploymentId = "deploymentId";
        String formPath = "forms/form-with-state";
        String submissionState = "submittedWithoutProperty";

        when(deploymentSvc.getResource(deploymentId, formPath + ".json")).thenReturn(new FileInputStream(getFile("forms/formWithState.json")));

        boolean actual = formioClient.shouldProcessSubmission(deploymentId, formPath, submissionState);

        assertTrue(actual);
    }

    @Test
    public void testFlatten_FormHasSubform() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubform.json";
        String childFormPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubform.json"));

        when(deploymentSvc.getResource("1", childFormPath)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath)));

        JsonNode actual = formioClient.expandSubforms(deploymentId, formDefinition);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testFlatten_FormHasSubformInContainer() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformInContainer.json";
        String childFormPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformInContainer.json"));

        when(deploymentSvc.getResource("1", childFormPath)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath)));

        JsonNode actual = formioClient.expandSubforms(deploymentId, formDefinition);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testFlatten_FormHasSubformsInArrays() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformsInArrays.json";
        String childFormPath = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformsInArrays.json"));

        when(deploymentSvc.getResource("1", childFormPath)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath)));

        JsonNode actual = formioClient.expandSubforms(deploymentId, formDefinition);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    @Test
    public void testFlatten_FormHasSubformInOtherSubform() throws IOException, URISyntaxException {
        String formPath = "forms/formWithSubformInAnotherSubform.json";
        String childFormPath1 = "formWithSubform.json";
        String childFormPath2 = "subform.json";
        String deploymentId = "1";
        JsonNode formDefinition = objectMapper.readTree(getFile(formPath));
        JsonNode expected = objectMapper.readTree(getFile("forms/formWithTransformedSubformInAnotherTransformedSubform.json"));

        when(deploymentSvc.getResource("1", childFormPath1)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath1)));
        when(deploymentSvc.getResource("1", childFormPath2)).thenReturn(new FileInputStream(getFile("forms/" + childFormPath2)));

        JsonNode actual = formioClient.expandSubforms(deploymentId, formDefinition);

        assertEquals(sortArray(expected.get("components")), sortArray(actual.get("components")));
    }

    private JsonNode sortArray(JsonNode arrayNode) {
        return objectMapper.valueToTree(getArrayElementStream((ArrayNode) arrayNode)
                .sorted(Comparator.comparing(objectNode -> objectNode.get("key").asText()))
                .map(this::sortObject)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll));
    }

    private JsonNode sortObject(JsonNode component) {
        return objectMapper.valueToTree(getFieldStream(component)
                .sorted(Map.Entry.comparingByKey())
                .map(field -> {
                    JsonNode fieldValue = field.getValue();
                    if (fieldValue instanceof ObjectNode) {
                        return new AbstractMap.SimpleEntry<>(field.getKey(), sortObject(fieldValue));
                    } else if (fieldValue instanceof ArrayNode) {
                        return new AbstractMap.SimpleEntry<>(field.getKey(), sortArray(fieldValue));
                    } else {
                        return field;
                    }
                })
                .collect(
                        LinkedHashMap::new,
                        (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll
                )
        );
    }

    private Stream<Map.Entry<String, JsonNode>> getFieldStream(JsonNode element) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(element.fields(), Spliterator.ORDERED), false);
    }

    private Stream<JsonNode> getArrayElementStream(ArrayNode arrayNode) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(arrayNode.elements(), Spliterator.ORDERED), false);
    }

}
