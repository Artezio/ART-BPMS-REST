package com.artezio.forms.formio;

import static java.util.AbstractMap.SimpleEntry;

import com.artezio.bpm.services.VariablesMapper;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.auth.AddJwtTokenRequestFilter;
import com.artezio.forms.formio.exceptions.FormNotFoundException;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.jackson.ObjectMapperProvider;
import com.artezio.logging.Log;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;

import net.minidev.json.JSONArray;

import org.apache.commons.text.CaseUtils;
import org.camunda.bpm.engine.variable.value.FileValue;
import com.artezio.camunda.spinjar.jackson.JacksonDataFormatConfigurator;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import spinjar.com.fasterxml.jackson.databind.DeserializationFeature;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
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

    private final static String FORMIO_SERVICE_PATH = System.getProperty("FORMIO_URL", "http://localhost:3001");
    private final static Map<String, FormComponent> FORMS_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private final static Map<Integer, Boolean> SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE = new ConcurrentHashMap<>();
    private final static ObjectMapper JSON_MAPPER = new ObjectMapper();
    //TODO rename
    private final static spinjar.com.fasterxml.jackson.databind.ObjectMapper MAPPER = new spinjar.com.fasterxml.jackson.databind.ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);
    
    private static ResteasyClient client;

    static {
        JacksonDataFormatConfigurator.registerSpinjarFileValueSerializers(MAPPER);
        client = new ResteasyClientBuilder()
                .connectionPoolSize(10)
                .register(new AddJwtTokenRequestFilter())
                .register(new ObjectMapperProvider())
                .build();
    }

    @Inject
    private VariablesMapper variablesMapper;

    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition with data for a form '{0}'")
    public String getFormWithData(String formPath, Map<String, Object> formVariables) {
        JsonNode form = getFormDefinitionSource(formPath);
        JsonNode data = cleanUnusedData(formPath, formVariables);
        ((ObjectNode) form).set("data", wrapSubformData(data, form));
        return form.toString();
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Uploading a form")
    public void uploadForm(String formDefinition) {
        String path = null;
        FormioService formioService = getFormioService();
        try {
            path = MAPPER.readTree(formDefinition).get("path").asText();
            formioService.createForm(formDefinition);
        } catch (BadRequestException bre) {
            formioService.getForm(path, true);
        } catch (IOException e) {
            throw new RuntimeException("Error while uploading a form", e);
        }
    }

    //TODO change output type. Map?
    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String formPath, Map<String, Object> submittedData,
            Map<String, Object> currentData) {
        try {
            submittedData = convertVariablesToFileRepresentations(formPath, submittedData);
            submittedData = enrichReadOnlyVariables(formPath, submittedData, currentData);
            JsonNode submittedFormioData = getFormioService()
                    .submission(formPath, toFormIoSubmissionData(submittedData))
                    .get("data");
            return unwrapSubformData(submittedFormioData, formPath).toString();
        } catch (BadRequestException ex) {
            throw new FormValidationException(getExceptionDetails(ex.getResponse()));
        } catch (Exception ex) {
            throw new FormValidationException(ex.getMessage());
        }
    }
    
    protected FormComponent getFormDefinition(String formPath) {
        return FORMS_CACHE.computeIfAbsent(formPath, path -> convertToFormComponent(getFormDefinitionSource(formPath)));
    }

    private FormComponent convertToFormComponent(JsonNode formSource) {
        try {
            return JSON_MAPPER.treeToValue(formSource, FormComponent.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Could not parse json form definition", e);
        }
    }
    
    private Map<String, Object> convertVariablesToFileRepresentations(String formName,
            Map<String, Object> submittedData) {
        return convertVariablesToFileRepresentations(submittedData, getFormDefinition(formName).toString());
    }

    //TODO rename
    @SuppressWarnings("unchecked")
    protected Map<String, Object> enrichReadOnlyVariables(String formName, Map<String, Object> editableData, Map<String, Object> readOnlyData)
            throws JsonProcessingException {
        return getComponentsValue(getFormDefinition(formName).getComponents(), editableData, readOnlyData);
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

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getContainerComponentValue(FormComponent formComponent, Map<String, Object> editableData,
            Map<String, Object> readOnlyData) {
        String componentKey = formComponent.getKey();
        editableData = (Map<String, Object>) editableData.get(componentKey);
        readOnlyData = (Map<String, Object>) readOnlyData.get(componentKey);
        Map<String, Object> containerValue = getComponentsValue(formComponent.getComponents(), editableData, readOnlyData);
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

    public boolean shouldProcessSubmittedData(String formPath, String submissionState) {
        FormComponent formDefinition = getFormDefinition(formPath);
        return SUBMITTED_DATA_PROCESSING_PROPERTY_CACHE.computeIfAbsent(
                Objects.hash(formDefinition, submissionState),
                key -> shouldProcessSubmittedData(formDefinition, submissionState));
    }

    private boolean shouldProcessSubmittedData(FormComponent formDefinition, String submissionState) {
        return formDefinition.getChildComponents().stream()
                .filter(component -> "saveState".equals(component.getAction()))
                .filter(component -> submissionState.equals(component.getState()))
                .map(FormComponent::getProperties)
                .map(properties -> properties.get("isSubmittedDataProcessed"))
                .map(Boolean.class::cast)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(true);
    }

    protected String getExceptionDetails(Response response) {
        if (!response.hasEntity()) {
            return "";
        }
        try {
            String rawResponseBody = response.readEntity(String.class);
            JsonNode responseBody = new ObjectMapper().readTree(rawResponseBody);
            if (!responseBody.at("/details").isMissingNode()) {
                return getStream(responseBody.get("details"))
                        .map(detail -> detail.get("message").asText())
                        .collect(Collectors.joining(", "));
            } else {
                return rawResponseBody;
            }
        } catch (IOException ignored) {
        }
        return "";
    }

    protected JsonNode getFormDefinitionSource(String formPath) throws FormNotFoundException {
        try {
            return getFormioService().getForm(formPath, true);
        } catch (BadRequestException bre) {
            throw new FormNotFoundException(formPath);
        }
    }

    protected static FormioService getFormioService() {
        ResteasyWebTarget target = client.target(UriBuilder.fromPath(FORMIO_SERVICE_PATH));
        return target.proxy(FormioService.class);
    }

    protected static String toValidFormioIdentifier(String identifier) {
        final String invalidExclusivePattern = "[^a-zA-Z0-9-]";
        return identifier.replaceAll(invalidExclusivePattern, "-");
    }

    protected static String toValidFormioPath(String path) {
        final String invalidExclusivePattern = "[^a-zA-Z0-9-/]";
        return path.replaceAll(invalidExclusivePattern, "-");
    }

    protected JsonNode unwrapSubformData(JsonNode data, String formPath) {
        FormComponent formDefinition = getFormDefinition(formPath);
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
    
    protected boolean isSubform(JsonNode definition) {
        JsonNode nodeType = definition.at("/type");
        return !nodeType.isMissingNode() && nodeType.asText().equals("form") && !definition.at("/src").isMissingNode();
    }

    protected boolean hasChildComponents(JsonNode definition) {
        return !definition.at("/components").isMissingNode();
    }

    protected JsonNode wrapSubformDataInArray(ArrayNode data, FormComponent formComponent) {
        data = data.deepCopy();
        for (int index = 0; index < data.size(); index++) {
            JsonNode wrappedElement = wrapSubformData(data.get(index), formComponent);
            data.set(index, wrappedElement);
        }
        return getWrappedComponents(data, formComponent);
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

    private JsonNode cleanUnusedData(String formPath, Map<String, Object> variables) {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        List<FormComponent> childComponentDefinitions = getFormDefinition(formPath).getChildComponents();
        Map<String, Object> wrappedObjects = getWrappedVariables(convertedVariables, childComponentDefinitions);
        try {
            return getFormioService()
                    .cleanUp(formPath, toFormIoSubmissionData(wrappedObjects))
                    .get("data");
        } catch (BadRequestException bre) {
            throw new FormNotFoundException(formPath);
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

    @Override
    public List<String> getFormVariableNames(String formPath) {
        return Optional.ofNullable(getFormDefinition(formPath).getComponents())
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(FormComponent::isInput)
                .filter(FormComponent::hasKey)
                .map(FormComponent::getKey)
                .collect(Collectors.toList());
    }

    @Override
    public String getFormId(String formPath) {
        return getFormDefinition(formPath).getId();
    }

}
