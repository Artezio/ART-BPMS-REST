package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.ServiceTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FormioClientTest extends ServiceTest {

    @Mock
    private DeploymentSvc deploymentSvc;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();

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
