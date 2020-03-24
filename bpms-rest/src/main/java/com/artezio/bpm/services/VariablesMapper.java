package com.artezio.bpm.services;

import com.artezio.logging.Log;
import com.fasterxml.jackson.databind.node.ObjectNode;
import spinjar.com.fasterxml.jackson.databind.DeserializationFeature;
import spinjar.com.fasterxml.jackson.databind.JsonNode;
import spinjar.com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Named;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
public class VariablesMapper {

    public final static String EXTENSION_NAME_PREFIX = "entity.";

    private final static com.fasterxml.jackson.databind.ObjectMapper JACKSON_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private final static ObjectMapper SPINJAR_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);

    @Log(level = CONFIG, beforeExecuteMessage = "Updating process variables")
    public void updateVariables(Map<String, Object> existedVariables, String inputVarsJson) throws IOException {
        SPINJAR_MAPPER.readTree(inputVarsJson).fields()
                .forEachRemaining(inputField -> updateVariable(inputField, existedVariables));
    }

    public com.fasterxml.jackson.databind.node.ObjectNode toJsonNode(Map<String, Object> variables) {
        try {
            return (ObjectNode) JACKSON_MAPPER.readTree(JACKSON_MAPPER.writeValueAsString(variables));
        } catch (IOException e) {
            throw new RuntimeException("Could not serialize variables");
        }
    }

    public Map<String, Object> convertVariablesToEntities(Map<String, Object> inputVariables, Map<String, String> existingEntitiesClassNames) {
        return inputVariables.entrySet().stream()
                .map(variable -> convertVariableToEntity(variable, existingEntitiesClassNames))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, Object> convertVariableToEntity(Map.Entry<String, Object> variable, Map<String, String> existingEntitiesClassNames) {
        String variableName = EXTENSION_NAME_PREFIX + variable.getKey();
        return existingEntitiesClassNames.containsKey(variableName)
                ? convertVariableToEntity(variable, existingEntitiesClassNames.get(variableName))
                : variable;
    }

    private Map.Entry<String, Object> convertVariableToEntity(Map.Entry<String, Object> variable, String existingEntityClassName) {
        try {
            String varName = variable.getKey();
            Object varValue = variable.getValue();
            Class<?> entityType = Class.forName(existingEntityClassName);
            String variableJsonValue = SPINJAR_MAPPER.writeValueAsString(varValue);
            varValue = SPINJAR_MAPPER.readValue(variableJsonValue, entityType);
            return new AbstractMap.SimpleEntry<>(varName, varValue);
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException("Error during converting variable to entity", e);
        }
    }

    private void updateVariable(Map.Entry<String, JsonNode> inputField, Map<String, Object> existedVariables) {
        try {
            String varName = inputField.getKey();
            JsonNode inputValue = inputField.getValue();
            Object result = updateVariable(existedVariables.get(varName), inputValue);
            existedVariables.put(varName, result);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Object updateVariable(Object target, JsonNode inputValue) throws IOException {
        return inputValue.isObject() && target != null
                ? SPINJAR_MAPPER.readerForUpdating(target).readValue(inputValue)
                : SPINJAR_MAPPER.convertValue(inputValue, Object.class);
    }

}
