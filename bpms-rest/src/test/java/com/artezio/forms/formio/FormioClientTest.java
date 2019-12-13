package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
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
import java.util.HashMap;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(MockitoJUnitRunner.class)
public class FormioClientTest extends ServiceTest {

    private static ResteasyClient resteasyClient = mock(ResteasyClient.class);

    @Mock
    private FormioService formioService;
    @Mock
    private ResteasyWebTarget restEasyWebTarget;
    @Mock
    private DeploymentSvc deploymentSvc;
    @InjectMocks
    private FormioClient formioClient = new FormioClient();

    @BeforeClass
    public static void initClass() throws NoSuchFieldException {
        Field restEasyClientField = FormioClient.class.getDeclaredField("client");
        setField(FormioClient.class, restEasyClientField, resteasyClient);
    }

    @Before
    public void init() {
        when(resteasyClient.target(any(UriBuilder.class))).thenReturn(restEasyWebTarget);
        when(restEasyWebTarget.proxy(FormioService.class)).thenReturn(formioService);
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        reset(resteasyClient);
        Field formsCacheField = FormioClient.class.getDeclaredField("FORMS_CACHE");
        Field submitButtonsCacheField = FormioClient.class.getDeclaredField("SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE");
        formsCacheField.setAccessible(true);
        submitButtonsCacheField.setAccessible(true);
        ((Map<String, JsonNode>) formsCacheField.get(FormioClient.class)).clear();
        ((Map<String, JsonNode>) submitButtonsCacheField.get(FormioClient.class)).clear();
    }

    @Test
    public void testUploadForm() {
        String formDefinition = "{\"formKey\":\"keeey\", \"path\":\"formPath\"}";

        formioClient.uploadForm(formDefinition);
    }

    @Test
    public void testUploadForm_formAlreadyExists() {
        String formPath = "/form1";
        String formDefinition = "{\"formKey\":\"keeey\", \"path\":\"" + formPath + "\"}";

        formioClient.uploadForm(formDefinition);
    }

    @Test
    public void testUploadForm_formDefinitionIsInvalid() {
        String formPath = "/form1";
        String formDefinition = "{\"formKey\":\"keeey\",\"path\":\"" + formPath + "\"}";

        when(formioService.createForm(formDefinition)).thenThrow(BadRequestException.class);

        formioClient.uploadForm(formDefinition);
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
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithState.json"));
        String formPath = "forms/form-with-state";
        String submissionState = "submitted";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        boolean actual = formioClient.shouldProcessSubmittedData(formPath, submissionState);

        assertTrue(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SubmissionStateIsCanceled() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithState.json"));
        String formPath = "forms/form-with-state";
        String submissionState = "canceled";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        boolean actual = formioClient.shouldProcessSubmittedData(formPath, submissionState);

        assertFalse(actual);
    }

    @Test
    public void shouldProcessSubmittedData_SkipDataProcessingPropertyNotSet() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithState.json"));
        String formPath = "forms/form-with-state";
        String submissionState = "submittedWithoutProperty";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        boolean actual = formioClient.shouldProcessSubmittedData(formPath, submissionState);

        assertTrue(actual);
    }

    @Test
    public void removeReadOnlyFieldsTest_SimpleFieldIsDisabled() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithDisabledFields.json"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("enabledField", "1");
        variables.put("disabledField", "1");
        variables.put("submit", true);
        Map<String, Object> expected = new HashMap<>();
        expected.put("enabledField", "1");
        expected.put("submit", true);
        String formPath = "forms/formWithDisabledFields";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        Map<String, Object> actual = formioClient.removeReadOnlyVariables(variables, formPath);

        assertEquals(expected, actual);
    }

    @Test
    public void removeReadOnlyFieldsTest_ContainerFieldIsDisabled() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithDisabledFields.json"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("disabledDataGrid", asList(new HashMap<String, Object>() {{ put("enabledField", "1"); }}));
        variables.put("submit", true);
        Map<String, Object> expected = new HashMap<>();
        expected.put("submit", true);
        String formPath = "forms/formWithDisabledFields";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        Map<String, Object> actual = formioClient.removeReadOnlyVariables(variables, formPath);

        assertEquals(expected, actual);
    }

    @Test
    public void removeReadOnlyFieldsTest_ContainerFieldHasDisabledComponents() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithDisabledFields.json"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("fieldContainer", new HashMap<String, Object>() {{ put("disabledField", "1"); put("enabledField", "1"); }});
        variables.put("submit", true);
        Map<String, Object> expected = new HashMap<>();
        expected.put("fieldContainer", new HashMap<String, Object>() {{ put("enabledField", "1"); }});
        expected.put("submit", true);
        String formPath = "forms/formWithDisabledFields";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        Map<String, Object> actual = formioClient.removeReadOnlyVariables(variables, formPath);

        assertEquals(expected, actual);
    }

    @Test
    public void removeReadOnlyFieldsTest_ArrayFieldsHaveDisabledComponents() throws IOException {
        JsonNode formDefinition = new ObjectMapper().readTree(getFile("forms/formWithDisabledFields.json"));
        Map<String, Object> variables = new HashMap<>();
        variables.put("disabledDataGrid", asList(new HashMap<String, Object>() {{ put("enabledField", "1"); }}));
        variables.put("fieldDataGrid", asList(new HashMap<String, Object>() {{ put("disabledField", "1"); put("enabledField", "1"); }}));
        variables.put("fieldEditGrid", asList(new HashMap<String, Object>() {{ put("disabledField", "1"); put("enabledField", "1"); }}));
        variables.put("submit", true);
        Map<String, Object> expected = new HashMap<>();
        expected.put("fieldDataGrid", asList(new HashMap<String, Object>() {{ put("enabledField", "1"); }}));
        expected.put("fieldEditGrid", asList(new HashMap<String, Object>() {{ put("enabledField", "1"); }}));
        expected.put("submit", true);
        String formPath = "forms/formWithDisabledFields";

        when(formioService.getForm(formPath, true)).thenReturn(formDefinition);

        Map<String, Object> actual = formioClient.removeReadOnlyVariables(variables, formPath);

        assertEquals(expected, actual);
    }

}
