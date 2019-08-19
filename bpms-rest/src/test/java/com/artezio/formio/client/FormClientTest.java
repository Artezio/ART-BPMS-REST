package com.artezio.formio.client;

import com.artezio.bpm.services.ServiceTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class FormClientTest extends ServiceTest {

    private static ResteasyClient resteasyClient = mock(ResteasyClient.class);

    @Mock
    private FormApi formApiProxy;
    @Mock
    private ResteasyWebTarget restEasyWebTarget;
    @InjectMocks
    private FormClient formio = new FormClient();

    private JsonNode formDefinitionNode = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/testForm.json"))));

    public FormClientTest() throws IOException {
    }

    @BeforeClass
    public static void initClass() throws NoSuchFieldException {
        Field restEasyClientField = FormClient.class.getDeclaredField("client");
        setField(FormClient.class, restEasyClientField, resteasyClient);
    }

    @Before
    public void init() {
        when(resteasyClient.target(any(UriBuilder.class))).thenReturn(restEasyWebTarget);
        when(restEasyWebTarget.proxy(FormApi.class)).thenReturn(formApiProxy);
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        reset(resteasyClient);
        Field formsCacheField = FormClient.class.getDeclaredField("FORMS_CACHE");
        Field submitButtonsCacheField = FormClient.class.getDeclaredField("SUBMIT_BUTTONS_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormClient.class)).clear();
    }

    @Test
    public void testGetFormDefinition_ThereIsNoFormsCacheHit() {
        JsonNode formDefinition = new ObjectNode(JsonNodeFactory.instance);
        String formPath = "formKey";

        when(formApiProxy.getForm(formPath, true)).thenReturn(formDefinition);

        JsonNode actual = formio.getFormDefinition(formPath);

        assertNotNull(actual);

        verify(formApiProxy, times(1)).getForm(formPath, true);
    }

    @Test
    public void testGetFormDefinition_ThereIsFormsCacheHit() throws NoSuchFieldException, IllegalAccessException {
        JsonNode formDefinition = new ObjectNode(JsonNodeFactory.instance);
        String formKey = "formKey";
        Field formsCacheField = FormClient.class.getDeclaredField("FORMS_CACHE");
        formsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormClient.class)).put(formKey, formDefinition);

        JsonNode actual = formio.getFormDefinition(formKey);

        assertNotNull(actual);

        verify(formApiProxy, never()).getForm(eq(formKey), eq(true));
    }

    @Test
    public void testShouldSkipValidation_StateWithValidationIsPassed() {
        String formKey = "formKey";
        String submissionState = "submitted";

        when(formApiProxy.getForm(formKey, true)).thenReturn(formDefinitionNode);

        boolean actual = formio.shouldSkipValidation(formKey, submissionState);

        assertFalse(actual);
    }

    @Test
    public void testShouldSkipValidation_StateWithNoValidationIsPassed() {
        String formKey = "formKey";
        String submissionState = "rejected";

        when(formApiProxy.getForm(formKey, true)).thenReturn(formDefinitionNode);

        boolean actual = formio.shouldSkipValidation(formKey, submissionState);

        assertTrue(actual);
    }

    @Test
    public void testShouldSkipValidation_PassedStateDoesntExist() {
        String formKey = "formKey";
        String submissionState = "notExistingState";

        when(formApiProxy.getForm(formKey, true)).thenReturn(formDefinitionNode);

        boolean actual = formio.shouldSkipValidation(formKey, submissionState);

        assertFalse(actual);
    }

    @Test
    public void testUploadForm() {
        String formDefinition = "{\"formKey\":\"keeey\"}";

        when(formApiProxy.createForm(formDefinition)).thenReturn(formDefinitionNode);

        formio.uploadForm(formDefinition);
    }

    @Test(expected = BadRequestException.class)
    public void testUploadForm_formAlreadyExists() {
        String formDefinition = "{\"formKey\":\"keeey\"}";

        when(formApiProxy.createForm(formDefinition)).thenThrow(BadRequestException.class);

        formio.uploadForm(formDefinition);
    }

    @Test
    public void testUploadFormIfNotExists() {
        String formDefinition = "{\"formKey\":\"keeey\", \"path\":\"formPath\"}";

        when(formApiProxy.createForm(formDefinition)).thenReturn(formDefinitionNode);

        formio.uploadFormIfNotExists(formDefinition);
    }

    @Test
    public void testUploadFormIfNotExists_formAlreadyExists() {
        String formPath = "/form1";
        String formDefinition = "{\"formKey\":\"keeey\", \"path\":\"" + formPath + "\"}";

        when(formApiProxy.createForm(formDefinition)).thenThrow(BadRequestException.class);
        when(formApiProxy.getForm(formPath, true)).thenReturn(formDefinitionNode);

        formio.uploadFormIfNotExists(formDefinition);
    }

    @Test(expected = BadRequestException.class)
    public void testUploadFormIfNotExists_formDefinitionIsInvalid() {
        String formPath = "/form1";
        String formDefinition = "{\"formKey\":\"keeey\", \"path\":\"" + formPath +"\"}";

        when(formApiProxy.createForm(formDefinition)).thenThrow(BadRequestException.class);
        when(formApiProxy.getForm("/form1", true)).thenThrow(BadRequestException.class);

        formio.uploadFormIfNotExists(formDefinition);
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
        JsonNode actual = formio.unwrapSubformData(submittedData, definition);
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
        JsonNode actual = formio.wrapSubformData(sourceData, definition);
        assertFalse(actual.at("/data").isMissingNode());
        assertFalse(actual.at("/data/nested-1/data").isMissingNode());
        assertFalse(actual.at("/data/nested-1/data/nested-2/data").isMissingNode());
        assertEquals("text2", actual.at("/data/nested-1/data/nested-2/data/nested-2-text").asText());
    }

}
