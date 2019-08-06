package com.artezio.bpm.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import junitx.framework.ListAssert;
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class VariablesMapperTest {

    @InjectMocks
    private VariablesMapper variablesMapper = new VariablesMapper();

    private String json = new String(Files.readAllBytes(Paths.get("./src/test/resources/testInput.json")));
    private JsonNode formDefinitionNode = new ObjectMapper().readTree(new String(Files.readAllBytes(Paths.get("./src/test/resources/testForm.json"))));

    public VariablesMapperTest() throws IOException {
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field fileComponentsCacheField = VariablesMapper.class.getDeclaredField("FILE_FIELDS_CACHE");
        fileComponentsCacheField.setAccessible(true);
        ((Map<Integer, Boolean>) fileComponentsCacheField.get(VariablesMapper.class)).clear();
    }

    @Test
    public void testUpdateVariables_PassedVariablesAreTheSameAsInputVariables() throws IOException {
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("var1", "1");
        }};
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", "2");
        }};
        String inputJson = "{\"var1\": \"2\"}";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_PassedVariableIsNull() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", "2");
        }};
        String inputJson = "{\"var1\": \"2\"}";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_NoPassedVariables() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", "2");
        }};
        String inputJson = "{\"var1\": \"2\"}";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_PassedVariablesAreNotTheSameAsInputVariables() throws IOException {
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("var1", "1");
        }};
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", "1");
            put("var2", "2");
        }};
        String inputJson = "{\"var2\": \"2\"}";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_ExecutionVariableDoesntExist_InputVariableIsContainer() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", new HashMap<String, Object>(){{
                put("var11", "value");
            }});
        }};
        String inputJson = "{\"var1\": { \"var11\": \"value\" } }";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_ExecutionVariableExists_InputVariableIsContainer() throws IOException {
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put("var1", new HashMap<String, Object>(){{
                put("var11", "value1");
            }});
        }};
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put("var1", new HashMap<String, Object>(){{
                put("var11", "value2");
            }});
        }};
        String inputJson = "{\"var1\": { \"var11\": \"value2\" } }";

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals(asList(expected.entrySet()), asList(variables.entrySet()));
    }

    @Test
    public void testUpdateVariables_PassedVariableIsFile_VariableIsNew() throws IOException {
        Map<String, Object> variables = new HashMap<>();
        String fileVariableName = "testFile";
        String fileName = "testFile.png";
        List<Map<String, Object>> fileValues = asList(getFileValue(getFile(fileName)));
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(fileVariableName, fileValues);
        }};
        String inputJson = toJson(expected);

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals((List) expected.get(fileVariableName), (List) variables.get(fileVariableName));
    }

    @Test
    public void testUpdateVariables_PassedVariableIsFile_VariableExists() throws IOException {
        String fileVariableName = "testFile";
        String fileName = "testFile.png";
        List<Map<String, Object>> fileValues = asList(getFileValue(getFile(fileName)));
        Map<String, Object> variables = new HashMap<String, Object>() {{
            put(fileVariableName, fileValues);
        }};
        Map<String, Object> expected = new HashMap<String, Object>() {{
            put(fileVariableName, fileValues);
        }};
        String inputJson = toJson(expected);

        variablesMapper.updateVariables(variables, inputJson);

        ListAssert.assertEquals((List) expected.get(fileVariableName), (List) variables.get(fileVariableName));
    }

    @Test
    public void testUpdateVariablesOnStringsCollection() throws IOException {
        Map<String, Object> target = new HashMap<>();
        target.put("stringArrayEntry", new String[]{"three", "four"});

        variablesMapper.updateVariables(target, json);

        ListAssert.assertEquals(asList("one", "two"), (List<String>) target.get("stringArrayEntry"));
    }

    @Test
    public void testUpdateVariablesOnNullString() throws IOException {
        Map<String, Object> target = new HashMap<>();
        target.put("emptyEntry", null);

        variablesMapper.updateVariables(target, json);

        assertEquals("empty value", target.get("emptyEntry"));
    }

    @Test
    public void testUpdateVariablesExistedString() throws IOException {
        Map<String, Object> target = new HashMap<>();
        target.put("stringEntry", "old value");

        variablesMapper.updateVariables(target, json);

        assertEquals("string value", target.get("stringEntry"));
    }

    @Test
    public void testUpdateVariablesOnNullComplexObject() throws IOException {
        Map<String, Object> target = new HashMap<>();
        target.put("objectEntry", null);

        variablesMapper.updateVariables(target, json);

        assertEquals("nested value", ((Map) ((Map) target.get("objectEntry")).get("nestedEntityField")).get("stringField"));
    }

    @Test
    public void testUpdateVariablesOnNullArray() throws IOException {
        Map<String, Object> target = new HashMap<>();
        target.put("stringArrayEntry", null);

        variablesMapper.updateVariables(target, json);

        ListAssert.assertEquals(asList("one", "two"), (List<String>) target.get("stringArrayEntry"));
    }

    private void assertFileValueEquals(FileValue expected, FileValue actual) throws IOException {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getFilename(), actual.getFilename());
        assertEquals(expected.getMimeType(), actual.getMimeType());
        if (expected.getValue() != null && actual.getValue() != null) {
            assertArrayEquals(IOUtils.toByteArray(expected.getValue()), IOUtils.toByteArray(actual.getValue()));
        } else {
            assertEquals(expected.getValue(), actual.getValue());
        }
    }

    private File getFile(String fileName) {
        return new File(getClass().getClassLoader().getResource(fileName).getFile());
    }

    private Map<String, Object> getFileValue(File file) throws IOException {
        String mimeType = Files.probeContentType(file.toPath());
        byte[] fileContent = Files.readAllBytes(file.toPath());
        String base64EncodedFileContent = Base64.getEncoder().encodeToString(fileContent);
        Map<String, Object> fileValue = new HashMap<>();
        fileValue.put("name", file.getName());
        fileValue.put("originalName", file.getName());
        fileValue.put("size", (int) file.length());
        fileValue.put("storage", "base64");
        fileValue.put("type", mimeType);
        fileValue.put("url", "data:" + mimeType + ";base64," + base64EncodedFileContent);

        return fileValue;
    }

    private String toJson(Object variable) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(variable);
    }

    private ArrayNode getFileFieldValues(List<File> files) throws IOException {
        ArrayNode filesNode = new ArrayNode(JsonNodeFactory.instance);
        for (File file : files) {
            filesNode.add(getBase64EncodedFileFieldValue(file));
        }
        return filesNode;
    }

    private JsonNode getBase64EncodedFileFieldValue(File file) throws IOException {
        String base64EncodedFileContent = Base64.getEncoder().encodeToString(Files.readAllBytes(file.toPath()));
        return new ObjectNode(JsonNodeFactory.instance)
                .put("storage", "base64")
                .put("name", "")
                .put("originalName", file.getName())
                .put("size", file.length())
                .put("type", Files.probeContentType(file.toPath()))
                .put("url", "data:image/png;base64," + base64EncodedFileContent);
    }
    
}
