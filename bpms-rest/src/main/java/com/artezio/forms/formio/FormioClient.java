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
import org.apache.commons.text.CaseUtils;
import org.camunda.bpm.engine.variable.value.FileValue;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
public class FormioClient implements FormClient {

    private final static Map<String, JsonNode> FORM_SOURCE_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, FormComponent> FORM_DEFINITION_CACHE = new ConcurrentHashMap<>();
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
            JsonNode form = getFormSource(deploymentId, formPath);
            JsonNode data = cleanUnusedData(deploymentId, formPath, variables);
            ((ObjectNode) form).set("data", wrapSubformData(data, form));
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
        String formDefinition = getFormSource(deploymentId, formPath).toString();
        Filter saveStateComponentsFilter = Filter.filter((Criteria.where("action").eq("saveState").and("state").eq(submissionState)));
        return SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE.computeIfAbsent(
                Objects.hash(formDefinition, submissionState),
                key -> shouldProcessSubmittedData(formDefinition, saveStateComponentsFilter));
    }

    private JsonNode getFormSource(String deploymentId, String formPath) {
        String formPathWithExt = !formPath.endsWith(".json") ? formPath.concat(".json") : formPath;
        return FORM_SOURCE_CACHE.computeIfAbsent(
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
        FormComponent formDefinition = getFormDefinition(deploymentId, formPath);
        return unwrapSubformData(data, formDefinition);
    }

    protected JsonNode unwrapSubformData(JsonNode data, FormComponent formDefinition) {
        if (formDefinition.hasComponents()) {
            List<FormComponent> childComponents = formDefinition.getChildComponents();
            if (data.isObject()) {
                return unwrapSubformDataFromObject(data, childComponents);
            }
            if (data.isArray()) {
                return unwrapSubformDataFromArray(data, childComponents, formDefinition);
            }
        }
        return data;
    }
    
    protected JsonNode wrapSubformData(JsonNode data, JsonNode formDefinitionSource) {
        FormComponent formDefinition = convertToFormComponent(formDefinitionSource);
        return wrapSubformData(data, formDefinition);
    }

    protected JsonNode wrapSubformData(JsonNode data, FormComponent formDefinition) {
        if (data.isObject()) {
            return wrapSubformDataInObject(data, formDefinition);
        }
        if (data.isArray()) {
            return wrapSubformDataInArray((ArrayNode) data, formDefinition);
        }
        return data;
    }

    protected JsonNode wrapSubformDataInObject(JsonNode data, FormComponent formComponent) {
        ObjectNode dataWithWrappedChildren = data.deepCopy();
        if (formComponent.hasComponents()) {
            List<FormComponent> childComponents = formComponent.getChildComponents();
            for (FormComponent child : childComponents) {
                String key = child.getKey();
                if (dataWithWrappedChildren.has(key)) {
                    dataWithWrappedChildren.set(key, wrapSubformData(dataWithWrappedChildren.get(key), child));
                }
            }
        }
        if (formComponent.isSubform()) {
            ObjectNode wrappedData = JsonNodeFactory.instance.objectNode();
            wrappedData.set("data", dataWithWrappedChildren);
            return wrappedData;
        }
        return dataWithWrappedChildren;
    }

    protected JsonNode wrapSubformDataInArray(ArrayNode data, FormComponent formComponent) {
        data = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            JsonNode wrappedElement = wrapSubformData(data.get(index), formComponent);
            data.set(index, wrappedElement);
        }
        return getWrappedComponents(data, formComponent);
    }

    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#isSubform()}
     */
    @Deprecated
    protected boolean isSubform(JsonNode definition) {
        JsonNode nodeType = definition.at("/type");
        return !nodeType.isMissingNode() && nodeType.asText().equals("form") && !definition.at("/src").isMissingNode();
    }

    protected boolean hasChildComponents(JsonNode definition) {
        return !definition.at("/components").isMissingNode();
    }
    
    
    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#getChildComponents()}
     */
    @Deprecated
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

    protected ArrayNode getArrayWithRemovedRedundantObjectElementWrappers(ArrayNode arrayNode, FormComponent formDefinition) {
        ArrayNode resultArrayNode = JsonNodeFactory.instance.arrayNode();
        getStream(arrayNode)
                .flatMap(this::getStream)
                .forEach(resultArrayNode::add);
        if (formDefinition.isArray()) {
            return transformElementsToFlatIfNecessary(resultArrayNode);
        } else {
            return resultArrayNode;
        }
    }

    private JsonNode cleanUnusedData(String deploymentId, String formPath, Map<String, Object> variables) throws IOException {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        FormComponent formDefinition = getFormDefinition(deploymentId, formPath);
        List<FormComponent> childComponentDefinitions = formDefinition.getChildComponents();
        Map<String, Object> wrappedObjects = getWrappedVariables(convertedVariables, childComponentDefinitions);
        String submissionData = MAPPER.writeValueAsString(toFormIoSubmissionData(wrappedObjects));
        JsonNode formSource = getFormSource(deploymentId, formPath);
        try (InputStream cleanUpResult = nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formSource.toString(), submissionData)) {
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

    private JsonNode unwrapSubformDataFromObject(JsonNode data, List<FormComponent> childComponents) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        for (FormComponent childDefinition : childComponents) {
            String key = childDefinition.getKey();
            if (data.has(key)) {
                JsonNode unwrappedData = unwrapSubformData(data, childDefinition, key);
                result.set(key, unwrappedData);
            }
        }
        return result;
    }

    private JsonNode unwrapSubformDataFromArray(JsonNode data, List<FormComponent> childComponents, FormComponent formDefinition) {
        ArrayNode unwrappedArray = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            ObjectNode currentNode = JsonNodeFactory.instance.objectNode();
            for (FormComponent childDefinition : childComponents) {
                String key = childDefinition.getKey();
                JsonNode unwrappedData = unwrapSubformData(data.get(index), childDefinition, key);
                currentNode.set(key, unwrappedData);
            }
            unwrappedArray.set(index, currentNode);
        }
        return getArrayWithRemovedRedundantObjectElementWrappers(unwrappedArray, formDefinition);
    }

    private JsonNode unwrapSubformData(JsonNode data, FormComponent childDefinition, String key) {
        if (!data.has(key)) {
            return data;
        }
        if (childDefinition.isSubform()) {
            data = data.get(key).get("data");
        } else {
            data = data.get(key);
        }
        return unwrapSubformData(data, childDefinition);
    }

    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#isContainer()}
     */
    @Deprecated
    private boolean isContainerComponent(JsonNode componentDefinition) {
        JsonNode type = componentDefinition.get("type");
        String componentType = type != null ? type.asText() : "";
        return componentType.equals("form") || componentType.equals("container") || componentType.equals("survey");
    }

    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#isArray()}
     */
    @Deprecated
    private boolean isArrayComponent(JsonNode componentDefinition) {
        JsonNode type = componentDefinition.get("type");
        String componentType = type != null ? type.asText() : "";
        return componentType.equals("datagrid") || componentType.equals("editgrid");
    }

    private Map<String, Object> getWrappedVariables(Map<String, Object> variables, List<FormComponent> components) {
        return variables.entrySet().stream()
                .map(variable -> {
                    String variableName = variable.getKey();
                    Object variableValue = variable.getValue();
                    Optional<FormComponent> component = findComponentByKey(variableName, components);
                    if (component.isPresent()) {
                        if (component.get().isContainer() && variableValue != null) {
                            variableValue = getContainerWithWrappedElements((Map<String, Object>) variableValue, component.get());
                        } else if (component.get().isArray() && ((List<Object>) variableValue).size() != 0) {
                            variableValue = getArrayWithWrappedElements((List<Object>) variableValue, component.get());
                        }
                    }
                    return new AbstractMap.SimpleEntry<>(variable.getKey(), variableValue);
                }).collect(HashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), HashMap::putAll);
    }

    private List<Object> getArrayWithWrappedElements(List<Object> variableValue, FormComponent component) {
        List<Object> arrayVariableWithWrappedElements = new ArrayList<>();
        List<FormComponent> arrayComponentElems = component.getChildComponents();
        for (int i = 0; i < arrayComponentElems.size(); i++) {
            FormComponent arrayComponentElem = arrayComponentElems.get(i);
            String arrayComponentElemKey = arrayComponentElem.getKey();
            if (arrayComponentElem.isContainer()) {
                Map<String, Object> wrappedContainer = new HashMap<>();
                Map<String, Object> value = getContainerWithWrappedElements((Map<String, Object>) variableValue.get(i), arrayComponentElem);
                wrappedContainer.put(arrayComponentElemKey, value);
                arrayVariableWithWrappedElements.add(wrappedContainer);
            } else if (arrayComponentElem.isArray()) {
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

    private Map<String, Object> getContainerWithWrappedElements(Map<String, Object> variableValue, FormComponent component) {
        Map<String, Object> wrappedElements = getWrappedVariables(variableValue, component.getChildComponents());
        return wrapIfNecessary(wrappedElements, component);
    }

    private Map<String, Object> getFieldsForWrappedComponents(Map<String, Object> variableFields, FormComponent component) {
        List<String> componentElementNames = component.getChildComponents().stream()
                .map(componentField -> componentField.getKey())
                .collect(Collectors.toList());
        return variableFields.entrySet().stream()
                .filter(entry -> !componentElementNames.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, Object> wrapIfNecessary(Map<String, Object> variableValue, FormComponent component) {
        if (component.mustBeWrapped()) {
            Map<String, Object> wrappedVariableValue = new HashMap<>();
            component.getChildComponents()
                    .forEach(childComponent -> {
                        String childComponentKey = childComponent.getKey();
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

    /**
     * @deprecated use {@link com.artezio.forms.formio.FormComponent#mustBeWrapped()}
     */
    @Deprecated
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

    private ArrayNode getWrappedComponents(ArrayNode arrayNode, FormComponent formComponent) {
        if (formComponent.isArray()) {
            arrayNode = getArrayWithWrappedByTypeElements(arrayNode);
        }
        return formComponent.mustBeWrapped()
                ? getArrayWithWrappedObjectElements(arrayNode, formComponent)
                : arrayNode;
    }
    
    //TODO remove deprecated method
    private ArrayNode getArrayWithWrappedObjectElements(ArrayNode arrayNode, FormComponent formComponent) {
        String objectWrapperName = formComponent.getComponents().get(0).getKey();
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

    private Optional<FormComponent> findComponentByKey(String key, List<FormComponent> components) {
        return components.stream()
                .filter(component -> component.getKey().equals(key))
                .findFirst();
    }

    private Stream<JsonNode> getStream(JsonNode node) {
        return StreamSupport.stream(node.spliterator(), false);
    }

    private Stream<Map.Entry<String, JsonNode>> getFieldsStream(JsonNode element) {
        return StreamSupport.stream(Spliterators
                .spliteratorUnknownSize(element.fields(), Spliterator.ORDERED), false);
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
    private Map<String, Object> convertVariablesToFileRepresentations(String objectVariableName, Map<String, Object> objectVariableAttributes, String formSource) {
        return objectVariableAttributes.entrySet().stream()
                .peek(objectAttribute -> {
                    Object attributeValue = objectVariableAttributes.get(objectAttribute.getKey());
                    String attributeName = objectAttribute.getKey();
                    String attributePath = !objectVariableName.isEmpty()//FIXME is attributePath used anywhere?
                            ? objectVariableName + "/" + attributeName
                            : attributeName;
                    if (isFileVariable(attributeName, formSource)) {
                        attributeValue = convertVariablesToFileRepresentations(attributeValue);
                    } else if (isObjectVariable(attributeValue)) {
                        attributeValue = convertVariablesToFileRepresentations(attributePath, (Map<String, Object>) attributeValue, formSource);
                    } else if (isArrayVariable(attributeValue)) {
                        attributeValue = convertListVariableToFileRepresentations(attributePath, (List<Object>) attributeValue, formSource);
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
                .peek(objectAttribute -> {
                    Object variableValue = formComponentVariables.get(objectAttribute.getKey());
                    String variableName = objectAttribute.getKey();
                    if (formComponent.containsFileComponent(variableName)) {
                        variableValue = toFileValues((List<Map<String, Object>>)variableValue);
                    } else if (isObjectVariable(variableValue)) {
                        variableValue = convertVariablesToFileRepresentations((Map<String, Object>) variableValue, formComponent);
                    } else if (isArrayVariable(variableValue)) {
                        variableValue = convertListVariableToFileRepresentations((List<Object>) variableValue, formComponent);
                    }
                    objectAttribute.setValue(variableValue);
                })
                .collect(HashMap::new, (m, e) -> m.put(e.getKey(), formComponentVariables.get(e.getKey())), HashMap::putAll);
    }
    
    @SuppressWarnings("unchecked")
    private List<Object> convertListVariableToFileRepresentations(List<Object> formComponentVariables, FormComponent formDefinition) {
        return formComponentVariables.stream()
                .map(variableValue -> {
                    if (isObjectVariable(variableValue)) {
                        return convertVariablesToFileRepresentations((Map<String, Object>) variableValue, formDefinition);
                    } else if (isArrayVariable(variableValue)) {
                        return ((List<Object>) variableValue).stream()
                                .map(objectVariable -> convertListVariableToFileRepresentations((List<Object>) variableValue, formDefinition))
                                .collect(Collectors.toList());
                    } else {
                        return variableValue;
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
    
    private List<FileValue> toFileValues(List<Map<String, Object>> fileVariableValue) {
        return fileVariableValue.stream()
                .map(this::toFileValue)
                .collect(Collectors.toList());
    }

    private FileValue toFileValue(Map<String, Object> fileVariableAttributes) {
        try {
            String attributesJson = MAPPER.writeValueAsString(fileVariableAttributes);
            return MAPPER.readValue(attributesJson, FileValue.class);
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
            submittedVariables = getVariablesWithReadOnly(deploymentId, formPath, submittedVariables, currentVariables);
            String submissionData = MAPPER.writeValueAsString(toFormIoSubmissionData(submittedVariables));
            String formSource = getFormSource(deploymentId, formPath).toString();
            try (InputStream validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formSource, submissionData)) {
                JsonNode cleanData = MAPPER.readTree(validationResult).get("data");
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
            return MAPPER.treeToValue(formSource, FormComponent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse json form definition", e);
        }
    }
    
    protected Map<String, Object> getVariablesWithReadOnly(String deploymentId, String formPath, Map<String, Object> editableData, Map<String, Object> readOnlyData)
            throws JsonProcessingException {
        return getComponentsValue(getFormDefinition(deploymentId, formPath).getComponents(), editableData, readOnlyData);
    }
    
    private Map<String, Object> getComponentsValue(List<FormComponent> formComponents, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        return Optional.ofNullable(formComponents)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(FormComponent::isInput)
                .filter(FormComponent::hasKey)
                .map(component -> getComponentValue(component, editableData, readOnlyData))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
    
    private Map.Entry<String, Object> getComponentValue(FormComponent component, Map<String, Object> editableData, Map<String, Object> readOnlyData) {
        if (component.isContainer()) {
            return getContainerComponentValue(component, editableData, readOnlyData);
        } else if (component.isArray()) {
            return getArrayComponentValue(component, editableData, readOnlyData);
        } else {
            return getSimpleComponentValue(component, editableData, readOnlyData);
        }
    }
    
    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getContainerComponentValue(FormComponent formComponent, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        String componentKey = formComponent.getKey();
        editableData = (Map<String, Object>) editableData.get(componentKey);
        readOnlyData = (Map<String, Object>) readOnlyData.get(componentKey);
        Map<String, Object> containerValue = getComponentsValue(formComponent.getComponents(), editableData, readOnlyData);
        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }
    
    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getArrayComponentValue(FormComponent formComponent, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        String componentKey = formComponent.getKey();
        List<Map<String, Object>> containerValue = new ArrayList<>();
        
        List<Map<String, Object>> editableArrayData = (List<Map<String, Object>>) editableData.get(componentKey);
        List<Map<String, Object>> readOnlyDataArrayData = (List<Map<String, Object>>) readOnlyData.get(componentKey);
        if (editableArrayData != null) {
            for (int i = 0; i < editableArrayData.size(); i++) {
                Map<String, Object> editableArrayItemData = editableArrayData.get(i);
                Map<String, Object> readOnlyDataArrayItemData = readOnlyDataArrayData.get(i);
                Map<String, Object> containerItemValue = getComponentsValue(formComponent.getComponents(), editableArrayItemData, readOnlyDataArrayItemData);
               containerValue.add(containerItemValue);
           }
        }
                
        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }
    
    private Map.Entry<String, Object> getSimpleComponentValue(FormComponent formComponent, Map<String, Object> editableData, Map<String, Object> readOnlyData) {

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
