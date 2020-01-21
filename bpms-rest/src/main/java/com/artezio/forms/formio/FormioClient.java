package com.artezio.forms.formio;

import static java.util.AbstractMap.SimpleEntry;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.VariablesMapper;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.jackson.ObjectMapperProvider;
import com.artezio.logging.Log;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import org.camunda.bpm.engine.variable.value.FileValue;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.artezio.logging.Log.Level.CONFIG;
import static java.lang.Boolean.TRUE;
import static java.util.Arrays.asList;

@Named
public class FormioClient implements FormClient {

    private final static Map<String, FormComponent> FORM_DEFINITION_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> SUBMISSION_PROCESSING_DECISIONS_CACHE = new ConcurrentHashMap<>();
    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "dryValidationAndCleanUp.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";
    private final static String GRID_NO_ROW_WRAPPING_PROPERTY = "noRowWrapping";

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);

    static {
        ObjectMapperProvider.registerFileValueSerializers(JSON_MAPPER);
    }

    @Inject
    private VariablesMapper variablesMapper;
    @Inject
    private DeploymentSvc deploymentSvc;
    @Inject
    private NodeJsProcessor nodeJsProcessor;

    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition with data for a form '{0}'")
    public String getFormWithData(String deploymentId, String formPath, Map<String, Object> variables) {
        try {
            JsonNode form = getFormSource(deploymentId, formPath);
            JsonNode data = cleanUnusedData(deploymentId, formPath, variables);
            ((ObjectNode) form).set("data", wrapGridData(data, form));
            return form.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
    }

    /**
     * @deprecated use {@link #dryValidationAndCleanup(String, String, Map, Map)}
     */
    @Deprecated
    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> variables) {
        try {
            String formDefinition = getFormSource(deploymentId, formPath).toString();
            variables = convertVariablesToFileRepresentations(variables, formDefinition);
            String submissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(variables));
            try (InputStream validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formDefinition, submissionData)) {
                JsonNode cleanData = JSON_MAPPER.readTree(validationResult).get("data");
                return unwrapGridData(cleanData, deploymentId, formPath).toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while dry validation and cleanup", e);
        }
    }

    public boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState) {
        String formDefinition = getFormSource(deploymentId, formPath).toString();
        String cacheKey = deploymentId + "-" + formPath + "-" + submissionState;
        return SUBMISSION_PROCESSING_DECISIONS_CACHE.computeIfAbsent(
                cacheKey,
                key -> shouldProcessSubmission(formDefinition, submissionState));
    }

    private JsonNode getFormSource(String deploymentId, String formPath) {
        String formPathWithExt = !formPath.endsWith(".json") ? formPath.concat(".json") : formPath;
        try {
            JsonNode form = JSON_MAPPER.readTree(deploymentSvc.getResource(deploymentId, formPathWithExt));
            return expandSubforms(deploymentId, form);
        } catch (IOException e) {
            throw new RuntimeException("Failed to get the form into the cache.", e);
        }

    }

    private JsonNode cleanUnusedData(String deploymentId, String formPath, Map<String, Object> variables) throws IOException {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        JsonNode formDefinition = getFormSource(deploymentId, formPath);
        String submissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(convertedVariables));
        try (InputStream cleanUpResult = nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), submissionData)) {
            return JSON_MAPPER.readTree(cleanUpResult)
                    .get("data");
        }
    }

    protected JsonNode expandSubforms(String deploymentId, JsonNode form) {
        Collector<JsonNode, ArrayNode, ArrayNode> arrayNodeCollector = Collector
                .of(JSON_MAPPER::createArrayNode, ArrayNode::add, ArrayNode::addAll);
        JsonNode components = toStream(form.get("components"))
                .map(expandSubforms(deploymentId))
                .collect(arrayNodeCollector);
        return ((ObjectNode) form).set("components", components);
    }

    private Function<JsonNode, JsonNode> expandSubforms(String deploymentId) {
        return component -> {
            if (isTypeOf(component, "container") || isArrayComponent(component)) {
                return expandSubforms(deploymentId, component);
            } else if (isFormComponent(component)) {
                return convertToContainer(deploymentId, component);
            } else {
                return component;
            }
        };
    }

    private JsonNode convertToContainer(String deploymentId, JsonNode form) {
        String formResourceName = form.get("key").asText() + ".json";
        JsonNode container = convertToContainer(form);
        JsonNode components = getFormSource(deploymentId, formResourceName).get("components").deepCopy();
        ((ObjectNode) container).replace("components", components);
        return container;
    }

    private JsonNode convertToContainer(JsonNode formDefinition) {
        List<String> formAttributes = asList("src", "reference", "form", "unique", "project", "path");
        Predicate<Map.Entry<String, JsonNode>> nonFormAttributesPredicate = field -> !formAttributes.contains(field.getKey());
        JsonNode container = JSON_MAPPER.valueToTree(toFieldStream(formDefinition)
                .filter(nonFormAttributesPredicate)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        ((ObjectNode) container).put("type", "container");
        ((ObjectNode) container).put("tree", true);
        return container;
    }

    //FIXME use FormComponent or JsonNode for formDefinitionJson
    private boolean shouldProcessSubmission(String formDefinitionJson, String submissionState) {
        Filter saveStateComponentsFilter = Filter.filter((Criteria.where("action").eq("saveState").and("state").eq(submissionState)));
        return (boolean) ((JSONArray) JsonPath.read(formDefinitionJson, "$..components[?]", saveStateComponentsFilter))
                .stream()
                .map(stateComponent -> (Map<String, Object>) stateComponent)
                .map(stateComponent -> (Map<String, Object>) stateComponent.get("properties"))
                .map(properties -> properties.get("isSubmittedDataProcessed"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(true);
    }

    protected JsonNode wrapGridData(JsonNode data, JsonNode definition) {
        if (data.isObject()) {
            return wrapGridDataInObject(data, definition);
        }
        if (data.isArray()) {
            return wrapGridDataInArray((ArrayNode) data, definition);
        }
        return data;
    }

    protected JsonNode wrapGridDataInObject(JsonNode data, JsonNode definition) {
        ObjectNode dataWithWrappedChildren = data.deepCopy();
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponentDefinitions(definition);
            for (JsonNode child : childComponents) {
                String key = child.get("key").asText();
                if (dataWithWrappedChildren.has(key)) {
                    dataWithWrappedChildren.set(key, wrapGridData(dataWithWrappedChildren.get(key), child));
                }
            }
        }
        return dataWithWrappedChildren;
    }

    protected JsonNode wrapGridDataInArray(ArrayNode data, JsonNode definition) {
        ArrayNode wrappedData = data.deepCopy();
        if (isGridUnwrapped(definition)) {
            String wrapperName = definition.at("/components/0/key").asText();
            for (int index = 0; index < data.size(); index++) {
                JsonNode arrayElement = data.get(index);
                ObjectNode wrapper = JsonNodeFactory.instance.objectNode();
                wrapper.set(wrapperName, arrayElement);
                wrappedData.set(index, wrapper);
            }
        }
        for (int index = 0; index < data.size(); index++) {
            JsonNode wrappedElement = wrapGridData(wrappedData.get(index), definition);
            wrappedData.set(index, wrappedElement);
        }
        return wrappedData;
    }

    protected boolean isGridUnwrapped(JsonNode definition) {
        JsonNode noRowWrappingProperty = definition.at(String.format("/properties/%s", GRID_NO_ROW_WRAPPING_PROPERTY));
        return isArrayComponent(definition)
                && !noRowWrappingProperty.isMissingNode()
                && TRUE.equals(noRowWrappingProperty.asBoolean());
    }

    protected boolean hasChildComponents(JsonNode definition) {
        return !definition.at("/components").isMissingNode();
    }


    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#getChildComponents()}
     */
    @Deprecated
    protected List<JsonNode> getChildComponentDefinitions(JsonNode definition) {
        final Set<String> layoutComponentTypes = new HashSet<>(asList("well", "table", "columns", "fieldset", "panel"));
        final Set<String> containerComponentTypes = new HashSet<>(asList("well", "fieldset", "panel"));
        List<JsonNode> nodes = new ArrayList<>();
        toStream(definition.get("components"))
                .filter(component -> !layoutComponentTypes.contains(component.get("type").asText()))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> containerComponentTypes.contains(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> "columns".equals(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("columns")))
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        toStream(definition.get("components"))
                .filter(component -> "table".equals(component.get("type").asText()))
                .flatMap(component -> toStream(component.get("rows")))
                .flatMap(this::toStream)
                .flatMap(component -> toStream(component.get("components")))
                .forEach(nodes::add);
        return nodes;
    }

    @SuppressWarnings("serial")
    protected Map<String, Object> toFormIoSubmissionData(Map<String, Object> variables) {
        return variables.containsKey("data")
                ? variables
                : new HashMap<String, Object>() {{
            put("data", variables);
        }};
    }

    protected JsonNode unwrapGridData(JsonNode data, String deploymentId, String formPath) {
        JsonNode formDefinition = getFormSource(deploymentId, formPath);
        return unwrapGridData(data, formDefinition);
    }

    protected JsonNode unwrapGridData(JsonNode data, JsonNode definition) {
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponentDefinitions(definition);
            if (data.isObject()) {
                return unwrapGridDataFromObject(data, childComponents);
            }
            if (data.isArray()) {
                return unwrapGridDataFromArray(data, childComponents);
            }
        }
        return data;
    }

    protected JsonNode unwrapGridDataFromObject(JsonNode data, List<JsonNode> childComponents) {
        ObjectNode unwrappedData = JsonNodeFactory.instance.objectNode();
        for (JsonNode childDefinition : childComponents) {
            String key = childDefinition.get("key").asText();
            if (data.has(key)) {
                unwrappedData.set(key, unwrapGridData(data, childDefinition, key));
            }
        }
        return unwrappedData;
    }

    protected JsonNode unwrapGridDataFromArray(JsonNode data, List<JsonNode> childComponents) {
        ArrayNode unwrappedArray = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            ObjectNode currentNode = JsonNodeFactory.instance.objectNode();
            for (JsonNode childDefinition : childComponents) {
                String key = childDefinition.get("key").asText();
                JsonNode unwrappedData = unwrapGridData(data.get(index), childDefinition, key);
                currentNode.set(key, unwrappedData);
            }
            unwrappedArray.set(index, currentNode);
        }
        return unwrappedArray;
    }

    protected JsonNode unwrapGridData(JsonNode data, JsonNode childDefinition, String key) {
        if (!data.has(key)) {
            return data;
        }
        data = unwrapGridData(data.get(key), childDefinition);
        if (isArrayComponent(childDefinition)) {
            data = unwrapGridData(childDefinition, (ArrayNode) data);
        }
        return data;
    }

    protected JsonNode unwrapGridData(JsonNode gridDefinition, ArrayNode data) {
        ArrayNode components = (ArrayNode) gridDefinition.get("components");
        ArrayNode unwrappedData = JsonNodeFactory.instance.arrayNode();
        JsonNode noRowWrappingProperty = gridDefinition.at(String.format("/properties/%s", GRID_NO_ROW_WRAPPING_PROPERTY));
        if (!noRowWrappingProperty.isMissingNode() && TRUE.equals(noRowWrappingProperty.asBoolean()) && (components.size() == 1)) {
            data.forEach(node -> unwrappedData.add(node.elements().next()));
        } else {
            unwrappedData.addAll(data);
        }
        return unwrappedData;
    }

    protected boolean isFormComponent(JsonNode componentDefinition) {
        return isTypeOf(componentDefinition, "form");
    }

    protected boolean isContainerComponent(JsonNode componentDefinition) {
        return isTypeOf(componentDefinition, "form")
                || isTypeOf(componentDefinition, "container")
                || isTypeOf(componentDefinition, "survey");
    }

    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#isArray()}
     */
    @Deprecated
    protected boolean isArrayComponent(JsonNode componentDefinition) {
        return isTypeOf(componentDefinition, "datagrid")
                || isTypeOf(componentDefinition, "editgrid");
    }

    protected boolean isTypeOf(JsonNode componentDefinition, String type) {
        JsonNode typeField = componentDefinition.get("type");
        String componentType = typeField != null ? typeField.asText() : "";
        return componentType.equals(type);
    }

    protected Stream<JsonNode> toStream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    protected Stream<Map.Entry<String, JsonNode>> toFieldStream(JsonNode node) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(node.fields(), Spliterator.ORDERED), false);
    }

    /**
     * @deprecated use {@link #convertVariablesToFileRepresentations(String, String, Map)}
     */
    @Deprecated
    private Map<String, Object> convertVariablesToFileRepresentations(Map<String, Object> taskVariables, String formSource) {
        return convertVariablesToFileRepresentations("", taskVariables, formSource);
    }

    private Map<String, Object> convertVariablesToFileRepresentations(String deploymentId, String formPath, Map<String, Object> taskVariables) {
        return convertVariablesToFileRepresentations(taskVariables, getFormDefinition(deploymentId, formPath));
    }

    /**
     * @deprecated use {@link #convertVariablesToFileRepresentations(Map, FormComponent)}
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    protected Map<String, Object> convertVariablesToFileRepresentations(String objectVariableName, Map<String, Object> objectVariableAttributes, String formDefinition) {
        return objectVariableAttributes.entrySet().stream()
                .peek(objectAttribute -> {
                    Object attributeValue = objectVariableAttributes.get(objectAttribute.getKey());
                    String attributeName = objectAttribute.getKey();
                    String attributePath = !objectVariableName.isEmpty()//FIXME is attributePath used anywhere?
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

    /**
     * @deprecated use {@link #convertListVariableToFileRepresentations(List, FormComponent)}
     */
    @Deprecated
    @SuppressWarnings("unchecked")
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


    @SuppressWarnings("unchecked")
    private Map<String, Object> convertVariablesToFileRepresentations(Map<String, Object> formComponentVariables, FormComponent formComponent) {
        return formComponentVariables.entrySet().stream()
                .peek(variable -> {
                    Object variableValue = formComponentVariables.get(variable.getKey());
                    String variableName = variable.getKey();
                    if (formComponent.containsFileComponent(variableName)) {
                        variableValue = toFileValues((List<Map<String, Object>>)variableValue);
                    } else if (isObjectVariable(variableValue)) {
                        variableValue = convertVariablesToFileRepresentations((Map<String, Object>) variableValue, formComponent);
                    } else if (isArrayVariable(variableValue)) {
                        variableValue = convertListVariableToFileRepresentations((List<Object>) variableValue, formComponent);
                    }
                    variable.setValue(variableValue);
                })
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), formComponentVariables.get(e.getKey())), HashMap::putAll);
    }

    @SuppressWarnings("unchecked")
    private List<Object> convertListVariableToFileRepresentations(List<Object> arrayFormComponentValues, FormComponent formComponent) {
        return arrayFormComponentValues.stream()
                .map(value -> {
                    if (isObjectVariable(value)) {
                        return convertVariablesToFileRepresentations((Map<String, Object>) value, formComponent);
                    } else if (isArrayVariable(value)) {
                        return ((List<Object>) value).stream()
                                .map(objectVariable -> convertListVariableToFileRepresentations((List<Object>) value, formComponent))
                                .collect(Collectors.toList());
                    } else {
                        return value;
                    }
                })
                .collect(Collectors.toList());
    }

    /**
     * @deprecated use {@link #toFileValues(List)}
     */
    @Deprecated
    private List<FileValue> convertVariablesToFileRepresentations(Object fileVariableValue) {
        return ((List<Map<String, Object>>) fileVariableValue).stream()
                .map(this::toFileValue)
                .collect(Collectors.toList());
    }

    private List<FileValue> toFileValues(List<Map<String, Object>> formFiles) {
        return formFiles.stream()
                .map(this::toFileValue)
                .collect(Collectors.toList());
    }

    private FileValue toFileValue(Map<String, Object> formFile) {
        try {
            String attributesJson = JSON_MAPPER.writeValueAsString(formFile);
            return JSON_MAPPER.readValue(attributesJson, FileValue.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not deserialize FileValue", e);
        }
    }

    /**
     * @deprecated use  {@link com.artezio.forms.formio.FormComponent#containsFileComponent(String)}
     */
    @Deprecated
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

    @Override
    public List<String> getFormVariableNames(String deploymentId, String formPath) {
        return Optional.ofNullable(getFormDefinition(deploymentId, formPath).getComponents())
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(FormComponent::isInput)
                .filter(FormComponent::hasKey)
                .map(FormComponent::getKey)
                .collect(Collectors.toList());
    }

    //TODO change output type to Map?
    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> submittedVariables,
            Map<String, Object> currentVariables) {
        try {
            submittedVariables = convertVariablesToFileRepresentations(deploymentId, formPath, submittedVariables);
            Map<String, Object> formVariables = getFormComponentVariables(deploymentId, formPath, submittedVariables, currentVariables);
            String submissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(formVariables));
            String formSource = getFormSource(deploymentId, formPath).toString();
            try (InputStream validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formSource, submissionData)) {
                JsonNode cleanData = JSON_MAPPER.readTree(validationResult).get("data");//TODO rename cleanData?
                return unwrapSubformData(cleanData, deploymentId, formPath).toString();
            }
        } catch (Exception ex) {
            throw new FormValidationException(ex.getMessage());
        }
    }

    protected FormComponent getFormDefinition(String deploymentId, String formPath) {
        return FORM_DEFINITION_CACHE.computeIfAbsent(formPath, path -> convertToFormComponent(getFormSource(deploymentId, formPath)));
    }

    private FormComponent convertToFormComponent(JsonNode formSource) {
        try {
            return JSON_MAPPER.treeToValue(formSource, FormComponent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse json form definition", e);
        }
    }

    //TODO rename editableData, readOnlyData?
    protected Map<String, Object> getFormComponentVariables(String deploymentId, String formPath, Map<String, Object> editableData, Map<String, Object> readOnlyData)
            throws JsonProcessingException {
        return getFormComponentVariables(getFormDefinition(deploymentId, formPath).getComponents(), editableData, readOnlyData);
    }

    //TODO remove clean up?
    private Map<String, Object> getFormComponentVariables(List<FormComponent> formComponents, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        return Optional.ofNullable(formComponents)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(FormComponent::isInput)
                .filter(FormComponent::hasKey)
                .map(component -> getFormComponentVariable(component, editableData, readOnlyData))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, Object> getFormComponentVariable(FormComponent component, Map<String, Object> editableData, Map<String, Object> readOnlyData) {
        if (component.isContainer()) {
            return getContainerComponentVariable(component, editableData, readOnlyData);
        } else if (component.isArray()) {
            return getArrayComponentVariable(component, editableData, readOnlyData);
        } else {
            return getSimpleComponentVariable(component, editableData, readOnlyData);
        }
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getContainerComponentVariable(FormComponent containerComponent, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        String componentKey = containerComponent.getKey();
        editableData = (Map<String, Object>) editableData.get(componentKey);
        readOnlyData = (Map<String, Object>) readOnlyData.get(componentKey);
        Map<String, Object> containerValue = getFormComponentVariables(containerComponent.getComponents(), editableData, readOnlyData);
        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getArrayComponentVariable(FormComponent arrayComponent, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        String componentKey = arrayComponent.getKey();
        List<Map<String, Object>> containerValue = new ArrayList<>();

        List<Map<String, Object>> editableArrayData = (List<Map<String, Object>>) editableData.get(componentKey);
        List<Map<String, Object>> readOnlyDataArrayData = (List<Map<String, Object>>) readOnlyData.get(componentKey);
        if (editableArrayData != null) {
            for (int i = 0; i < editableArrayData.size(); i++) {
                Map<String, Object> editableArrayItemData = editableArrayData.get(i);
                Map<String, Object> readOnlyDataArrayItemData = readOnlyDataArrayData.get(i);
                Map<String, Object> containerItemValue = getFormComponentVariables(arrayComponent.getComponents(), editableArrayItemData, readOnlyDataArrayItemData);
               containerValue.add(containerItemValue);
           }
        }

        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }

    private Map.Entry<String, Object> getSimpleComponentVariable(FormComponent formComponent, Map<String, Object> editableData, Map<String, Object> readOnlyData) {

        String componentKey = formComponent.getKey();
        Entry<String, Object> editableDataEntry = Optional.ofNullable(editableData)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(entry -> componentKey.equals(entry.getKey()))
                .findFirst()
                .orElse(null);
        Entry<String, Object> readOnlyDataEntry = Optional.ofNullable(readOnlyData)
                .map(Map::entrySet)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(entry -> componentKey.equals(entry.getKey()))
                .findFirst()
                .orElse(null);

        return formComponent.isEditable() ? editableDataEntry : readOnlyDataEntry;
    }


}
