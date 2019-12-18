package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.VariablesMapper;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.jackson.ObjectMapperProvider;
import com.artezio.logging.Log;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.Criteria;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.apache.commons.text.CaseUtils;
import org.camunda.bpm.engine.variable.value.FileValue;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
public class FormioClient implements FormClient {

    private final static Map<String, JsonNode> FORM_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private final static Map<Integer, Boolean> SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE = new ConcurrentHashMap<>();
    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "dryValidationAndCleanUp.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";

    private final static ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);

    static {
        ObjectMapperProvider.registerFileValueSerializers(MAPPER);
    }

    @Inject
    private VariablesMapper variablesMapper;
    @Inject
    private DeploymentSvc deploymentSvc;
    @Inject
    private NodeJsProcessor nodeJsProcessor;

    //TODO implement getting parent form with its subform in a flat form
    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition with data for a form '{0}'")
    public String getFormWithData(String deploymentId, String formPath, Map<String, Object> variables) {
        try {
            JsonNode form = getForm(deploymentId, formPath);
            JsonNode data = cleanUnusedData(deploymentId, formPath, variables);
            ((ObjectNode) form).set("data", wrapSubformData(data, form));
            return form.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> variables) {
        try {
            String formDefinition = getForm(deploymentId, formPath).toString();
            variables = convertVariablesToFileRepresentations(variables, formDefinition);
            String submissionData = MAPPER.writeValueAsString(toFormIoSubmissionData(variables));
            try (InputStream validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition, submissionData)) {
                JsonNode cleanData = MAPPER.readTree(validationResult).get("data");
                return unwrapSubformData(cleanData, deploymentId, formPath).toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while dry validation and cleanup", e);
        }
    }

    public boolean shouldProcessSubmittedData(String deploymentId, String formPath, String submissionState) {
        String formDefinition = getForm(deploymentId, formPath).toString();
        Filter saveStateComponentsFilter = Filter.filter((Criteria.where("action").eq("saveState").and("state").eq(submissionState)));
        return SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE.computeIfAbsent(
                Objects.hash(formDefinition, submissionState),
                key -> shouldProcessSubmittedData(formDefinition, saveStateComponentsFilter));
    }

    private JsonNode getForm(String deploymentId, String formPath) {
        String formPathWithExt = !formPath.endsWith(".json") ? formPath.concat(".json") : formPath;
        return FORM_CACHE.computeIfAbsent(
                String.valueOf(Objects.hash(deploymentId + formPath)),
                cacheKey -> {
                    try {
                        return MAPPER.readTree(deploymentSvc.getResource(deploymentId, formPathWithExt));
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to get a form into the cache.", e);
                    }
                });
    }

    private boolean shouldProcessSubmittedData(String formDefinitionJson, Filter saveStateComponentsFilter) {
        return (boolean) ((JSONArray) JsonPath.read(formDefinitionJson, "$..components[?]", saveStateComponentsFilter))
                .stream()
                .map(stateComponent -> (Map<String, Object>) stateComponent)
                .map(stateComponent -> (Map<String, Object>) stateComponent.get("properties"))
                .map(properties -> properties.get("isSubmittedDataProcessed"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(true);
    }

    protected JsonNode unwrapSubformData(JsonNode data, String deploymentId, String formPath) {
        JsonNode formDefinition = getForm(deploymentId, formPath);
        return unwrapSubformData(data, formDefinition);
    }

    protected JsonNode unwrapSubformData(JsonNode data, JsonNode definition) {
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponentDefinitions(definition);
            if (data.isObject()) {
                return unwrapSubformDataFromObject(data, childComponents);
            }
            if (data.isArray()) {
                return unwrapSubformDataFromArray(data, childComponents, definition);
            }
        }
        return data;
    }

    protected JsonNode wrapSubformData(JsonNode data, JsonNode definition) {
        if (data.isObject()) {
            return wrapSubformDataInObject(data, definition);
        }
        if (data.isArray()) {
            return wrapSubformDataInArray((ArrayNode) data, definition);
        }
        return data;
    }

    protected JsonNode wrapSubformDataInObject(JsonNode data, JsonNode definition) {
        ObjectNode dataWithWrappedChildren = data.deepCopy();
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponentDefinitions(definition);
            for (JsonNode child : childComponents) {
                String key = child.get("key").asText();
                if (dataWithWrappedChildren.has(key)) {
                    dataWithWrappedChildren.set(key, wrapSubformData(dataWithWrappedChildren.get(key), child));
                }
            }
        }
        if (isSubform(definition)) {
            ObjectNode wrappedData = JsonNodeFactory.instance.objectNode();
            wrappedData.set("data", dataWithWrappedChildren);
            return wrappedData;
        }
        return dataWithWrappedChildren;
    }

    protected JsonNode wrapSubformDataInArray(ArrayNode data, JsonNode definition) {
        data = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            JsonNode wrappedElement = wrapSubformData(data.get(index), definition);
            data.set(index, wrappedElement);
        }
        return getWrappedComponents(data, definition);
    }

    protected boolean isSubform(JsonNode definition) {
        JsonNode nodeType = definition.at("/type");
        return !nodeType.isMissingNode() && nodeType.asText().equals("form") && !definition.at("/src").isMissingNode();
    }

    protected boolean hasChildComponents(JsonNode definition) {
        return !definition.at("/components").isMissingNode();
    }

    protected List<JsonNode> getChildComponentDefinitions(JsonNode definition) {
        final Set<String> layoutComponentTypes = new HashSet<>(Arrays.asList("well", "table", "columns", "fieldset", "panel"));
        final Set<String> containerComponentTypes = new HashSet<>(Arrays.asList("well", "fieldset", "panel"));
        List<JsonNode> nodes = new ArrayList<>();
        getStream(definition.get("components"))
                .filter(component -> !layoutComponentTypes.contains(component.get("type").asText()))
                .forEach(nodes::add);
        getStream(definition.get("components"))
                .filter(component -> containerComponentTypes.contains(component.get("type").asText()))
                .flatMap(component -> getStream(component.get("components")))
                .forEach(nodes::add);
        getStream(definition.get("components"))
                .filter(component -> "columns".equals(component.get("type").asText()))
                .flatMap(component -> getStream(component.get("columns")))
                .flatMap(component -> getStream(component.get("components")))
                .forEach(nodes::add);
        getStream(definition.get("components"))
                .filter(component -> "table".equals(component.get("type").asText()))
                .flatMap(component -> getStream(component.get("rows")))
                .flatMap(this::getStream)
                .flatMap(component -> getStream(component.get("components")))
                .forEach(nodes::add);
        return nodes;
    }

    protected ArrayNode getArrayWithRemovedRedundantObjectElementWrappers(ArrayNode arrayNode, JsonNode formDefinition) {
        ArrayNode resultArrayNode = JsonNodeFactory.instance.arrayNode();
        getStream(arrayNode)
                .flatMap(this::getStream)
                .forEach(resultArrayNode::add);
        if (isArrayComponent(formDefinition)) {
            return transformElementsToFlatIfNecessary(resultArrayNode);
        } else {
            return resultArrayNode;
        }
    }

    private JsonNode cleanUnusedData(String deploymentId, String formPath, Map<String, Object> variables) throws IOException {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        JsonNode formDefinition = getForm(deploymentId, formPath);
        List<JsonNode> childComponentDefinitions = getChildComponentDefinitions(formDefinition);
        Map<String, Object> wrappedObjects = getWrappedVariables(convertedVariables, childComponentDefinitions);
        String submissionData = MAPPER.writeValueAsString(toFormIoSubmissionData(wrappedObjects));
        try (InputStream cleanUpResult = nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), submissionData)) {
            return MAPPER.readTree(cleanUpResult)
                    .get("data");
        }
    }

    @SuppressWarnings("serial")
    private Map<String, Object> toFormIoSubmissionData(Map<String, Object> variables) {
        return variables.containsKey("data")
                ? variables
                : new HashMap<String, Object>() {{
            put("data", variables);
        }};
    }

    private JsonNode unwrapSubformDataFromObject(JsonNode data, List<JsonNode> childComponents) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (JsonNode childDefinition : childComponents) {
            String key = childDefinition.get("key").asText();
            if (data.has(key)) {
                JsonNode unwrappedData = unwrapSubformData(data, childDefinition, key);
                result.set(key, unwrappedData);
            }
        }
        return result;
    }

    private JsonNode unwrapSubformDataFromArray(JsonNode data, List<JsonNode> childComponents, JsonNode formDefinition) {
        ArrayNode unwrappedArray = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            ObjectNode currentNode = JsonNodeFactory.instance.objectNode();
            for (JsonNode childDefinition : childComponents) {
                String key = childDefinition.get("key").asText();
                JsonNode unwrappedData = unwrapSubformData(data.get(index), childDefinition, key);
                currentNode.set(key, unwrappedData);
            }
            unwrappedArray.set(index, currentNode);
        }
        return getArrayWithRemovedRedundantObjectElementWrappers(unwrappedArray, formDefinition);
    }

    private JsonNode unwrapSubformData(JsonNode data, JsonNode childDefinition, String key) {
        if (!data.has(key)) {
            return data;
        }
        if (isSubform(childDefinition)) {
            data = data.get(key).get("data");
        } else {
            data = data.get(key);
        }
        return unwrapSubformData(data, childDefinition);
    }

    private boolean isContainerComponent(JsonNode componentDefinition) {
        JsonNode type = componentDefinition.get("type");
        String componentType = type != null ? type.asText() : "";
        return componentType.equals("form") || componentType.equals("container") || componentType.equals("survey");
    }

    private boolean isArrayComponent(JsonNode componentDefinition) {
        JsonNode type = componentDefinition.get("type");
        String componentType = type != null ? type.asText() : "";
        return componentType.equals("datagrid") || componentType.equals("editgrid");
    }

    private Map<String, Object> getWrappedVariables(Map<String, Object> variables, List<JsonNode> components) {
        return variables.entrySet().stream()
                .map(variable -> {
                    String variableName = variable.getKey();
                    Object variableValue = variable.getValue();
                    Optional<JsonNode> component = findComponentByKey(variableName, components);
                    if (component.isPresent()) {
                        if (isContainerComponent(component.get()) && variableValue != null) {
                            variableValue = getContainerWithWrappedElements((Map<String, Object>) variableValue, component.get());
                        } else if (isArrayComponent(component.get()) && ((List<Object>) variableValue).size() != 0) {
                            variableValue = getArrayWithWrappedElements((List<Object>) variableValue, component.get());
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(variable.getKey(), variableValue);
                }).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    private List<Object> getArrayWithWrappedElements(List<Object> variableValue, JsonNode component) {
        List<Object> arrayVariableWithWrappedElements = new ArrayList<>();
        List<JsonNode> arrayComponentElems = getChildComponentDefinitions(component);
        for (int i = 0; i < arrayComponentElems.size(); i++) {
            JsonNode arrayComponentElem = arrayComponentElems.get(i);
            String arrayComponentElemKey = arrayComponentElem.get("key").asText();
            if (isContainerComponent(arrayComponentElem)) {
                Map<String, Object> wrappedContainer = new HashMap<>();
                Map<String, Object> value = getContainerWithWrappedElements((Map<String, Object>) variableValue.get(i), arrayComponentElem);
                wrappedContainer.put(arrayComponentElemKey, value);
                arrayVariableWithWrappedElements.add(wrappedContainer);
            } else if (isArrayComponent(arrayComponentElem)) {
                Map<String, Object> wrappedArray = new HashMap<>();
                List<Object> value = getArrayWithWrappedElements((List<Object>) variableValue.get(i), arrayComponentElem);
                wrappedArray.put(arrayComponentElemKey, value);
                arrayVariableWithWrappedElements.add(wrappedArray);
            } else {
                arrayVariableWithWrappedElements.add(variableValue.get(i));
            }
        }
        return arrayVariableWithWrappedElements;
    }

    private Map<String, Object> getContainerWithWrappedElements(Map<String, Object> variableValue, JsonNode component) {
        Map<String, Object> wrappedElements = getWrappedVariables(variableValue, getChildComponentDefinitions(component));
        return wrapIfNecessary(wrappedElements, component);
    }

    private Map<String, Object> getFieldsForWrappedComponents(Map<String, Object> variableFields, JsonNode component) {
        List<String> componentElementNames = getChildComponentDefinitions(component).stream()
                .map(componentField -> componentField.get("key").asText())
                .collect(Collectors.toList());
        return variableFields.entrySet().stream()
                .filter(entry -> !componentElementNames.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Object> wrapIfNecessary(Map<String, Object> variableValue, JsonNode component) {
        if (isMustBeWrapped(component)) {
            Map<String, Object> wrappedVariableValue = new HashMap<>();
            getChildComponentDefinitions(component)
                    .forEach(childComponent -> {
                        String childComponentKey = childComponent.get("key").asText();
                        if (variableValue.containsKey(childComponentKey)) {
                            wrappedVariableValue.put(childComponentKey, variableValue.get(childComponentKey));
                        }
                    });
            String wrapperName = getWrapperName(variableValue);
            Map<String, Object> wrappedComponentFields = getFieldsForWrappedComponents(variableValue, component);
            wrappedVariableValue.put(wrapperName, wrappedComponentFields);
            return wrappedVariableValue;
        } else {
            return variableValue;
        }
    }

    private boolean isMustBeWrapped(JsonNode componentDefinition) {
        try {
            return getChildComponentDefinitions(componentDefinition).stream()
                    .anyMatch(childComponent -> childComponent.get("key").asText().equals("flat"));
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private String getWrapperName(Map<String, Object> variable) {
        return CaseUtils.toCamelCase((String) variable.get("type"), false, '_');
    }

    private ArrayNode getWrappedComponents(ArrayNode arrayNode, JsonNode componentDefinition) {
        if (isArrayComponent(componentDefinition)) {
            arrayNode = getArrayWithWrappedByTypeElements(arrayNode);
        }
        return isMustBeWrapped(componentDefinition)
                ? getArrayWithWrappedObjectElements(arrayNode, componentDefinition)
                : arrayNode;
    }

    private ArrayNode getArrayWithWrappedObjectElements(ArrayNode arrayNode, JsonNode componentDefinition) {
        String objectWrapperName = componentDefinition.at("/components/0/key").asText();
        ArrayNode arrayNodeWithWrappedObjects = JsonNodeFactory.instance.arrayNode();
        arrayNode.forEach(element -> {
            ObjectNode objectWrapperNode = JsonNodeFactory.instance.objectNode();
            ObjectNode objectNode = objectWrapperNode.putObject(objectWrapperName);
            element.fields().forEachRemaining(elementField ->
                    objectNode.put(elementField.getKey(), elementField.getValue()));
            arrayNodeWithWrappedObjects.add(objectWrapperNode);
        });
        return arrayNodeWithWrappedObjects;
    }

    private ArrayNode transformElementsToFlatIfNecessary(ArrayNode arrayNode) {
        ArrayNode resultArrayNode = JsonNodeFactory.instance.arrayNode();
        arrayNode.forEach(element -> {
            if (element.has("flat")) {
                Optional<Map.Entry<String, JsonNode>> searchResult = getFieldsStream(element)
                        .filter(entry -> entry.getValue().isObject())
                        .findFirst();
                if (searchResult.isPresent()) {
                    Map.Entry<String, JsonNode> documentField = searchResult.get();
                    ((ObjectNode) element).remove(documentField.getKey());
                    documentField.getValue().fields()
                            .forEachRemaining(field -> ((ObjectNode) element).put(field.getKey(), field.getValue()));
                    resultArrayNode.add(element);
                }
            }
        });
        return resultArrayNode.size() != 0
                ? resultArrayNode
                : arrayNode;
    }

    private ArrayNode getArrayWithWrappedByTypeElements(ArrayNode arrayNode) {
        return JsonNodeFactory.instance.arrayNode().addAll(
                getStream(arrayNode)
                        .filter(element -> element.has("flat"))
                        .map(element -> {
                            List<Map.Entry<String, JsonNode>> fields = getFieldsStream(element)
                                    .filter(field -> !field.getKey().equals("type"))
                                    .collect(Collectors.toList());
                            String documentWrapperName = element.get("type").asText();
                            element = ((ObjectNode) element).retain("type");
                            ObjectNode documentWrapper = ((ObjectNode) element).putObject(documentWrapperName);
                            fields.forEach(field -> documentWrapper.put(field.getKey(), field.getValue()));
                            return element;
                        })
                        .collect(Collectors.toList()));
    }

    private Optional<JsonNode> findComponentByKey(String key, List<JsonNode> components) {
        return components.stream()
                .filter(component -> component.get("key").asText().equals(key))
                .findFirst();
    }

    private Stream<JsonNode> getStream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private Stream<Map.Entry<String, JsonNode>> getFieldsStream(JsonNode element) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(element.fields(), Spliterator.ORDERED), false);
    }

    private Map<String, Object> convertVariablesToFileRepresentations(Map<String, Object> taskVariables, String formDefinition) {
        return convertVariablesToFileRepresentations("", taskVariables, formDefinition);
    }

    private Map<String, Object> convertVariablesToFileRepresentations(String objectVariableName, Map<String, Object> objectVariableAttributes, String formDefinition) {
        return objectVariableAttributes.entrySet().stream()
                .peek(objectAttribute -> {
                    Object attributeValue = objectVariableAttributes.get(objectAttribute.getKey());
                    String attributeName = objectAttribute.getKey();
                    String attributePath = !objectVariableName.isEmpty()
                            ? objectVariableName + "/" + attributeName
                            : attributeName;
                    if (isFileVariable(attributeName, formDefinition)) {
                        attributeValue = convertVariablesToFileRepresentations(attributeValue);
                    } else if (isObjectVariable(attributeValue)) {
                        attributeValue = convertVariablesToFileRepresentations(attributePath, (Map<String, Object>) attributeValue, formDefinition);
                    } else if (isArrayVariable(attributeValue)) {
                        attributeValue = convertListVariableToFileRepresentations(attributePath, (List<Object>) attributeValue, formDefinition);
                    }
                    objectAttribute.setValue(attributeValue);
                })
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), objectVariableAttributes.get(e.getKey())), HashMap::putAll);
    }

    private List<Object> convertListVariableToFileRepresentations(String attributePath, List<Object> variableValue, String formDefinition) {
        return variableValue.stream()
                .map(element -> {
                    if (isObjectVariable(element)) {
                        return convertVariablesToFileRepresentations(attributePath, (Map<String, Object>) element, formDefinition);
                    } else if (isArrayVariable(element)) {
                        return ((List<Object>) element).stream()
                                .map(objectVariable -> convertListVariableToFileRepresentations(attributePath + "[*]", (List<Object>) element, formDefinition))
                                .collect(Collectors.toList());
                    } else {
                        return element;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<FileValue> convertVariablesToFileRepresentations(Object fileVariableValue) {
        return ((List<Map<String, Object>>) fileVariableValue).stream()
                .map(this::toFileValue)
                .collect(Collectors.toList());
    }

    private FileValue toFileValue(Map<String, Object> attributes) {
        try {
            String attributesJson = MAPPER.writeValueAsString(attributes);
            return MAPPER.readValue(attributesJson, FileValue.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not deserialize FileValue", e);
        }
    }

    private boolean isFileVariable(String variableName, String formDefinition) {
        Function<String, JSONArray> fileFieldSearch = key -> JsonPath.read(formDefinition, String.format("$..[?(@.type == 'file' && @.key == '%s')]", variableName));
        JSONArray fileField = FILE_FIELDS_CACHE.computeIfAbsent(formDefinition + "." + variableName, fileFieldSearch);
        return !fileField.isEmpty();
    }

    private boolean isArrayVariable(Object variableValue) {
        return variableValue instanceof List;
    }

    private boolean isObjectVariable(Object variableValue) {
        return variableValue instanceof Map;
    }

}
