package com.artezio.formio.client;

import com.artezio.bpm.services.VariablesMapper;
import com.artezio.formio.client.auth.AddJwtTokenRequestFilter;
import com.artezio.formio.client.exceptions.FormNotFoundException;
import com.artezio.formio.client.exceptions.FormValidationException;
import com.artezio.logging.Log;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import net.minidev.json.JSONArray;
import org.apache.commons.text.CaseUtils;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.artezio.logging.Log.Level.CONFIG;

@Named
public class FormClient {

    private final static String FORMIO_SERVER_PATH = System.getProperty("FORMIO_URL", "http://localhost:3001");
    private final static Map<String, JsonNode> FORMS_CACHE = new ConcurrentHashMap<>();
    private final static Map<Integer, JSONArray> SUBMIT_BUTTONS_CACHE = new ConcurrentHashMap<>();

    private static ResteasyClient client = new ResteasyClientBuilder()
            .connectionPoolSize(10)
            .register(new AddJwtTokenRequestFilter())
            .build();

    @Inject
    private VariablesMapper variablesMapper;

    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition with data for form '{0}'")
    public String getFormWithData(String formPath, Map<String, Object> variables) {
        JsonNode form = getForm(formPath);
        JsonNode data = cleanUnusedData(formPath, variables);
        ((ObjectNode) form).set("data", wrapSubformData(data, form));
        return form.toString();
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Uploading form '{0}'")
    public void uploadFormIfNotExists(String path, String formDefinition) {
        try {
            uploadForm(formDefinition);
        } catch (BadRequestException bre) {
            // BadRequest is thrown for both cases: 1) form already exists; 2) form definition is invalid
            // Try to load the form. If the form not exists, an exception will be thrown again to show that form definition is invalid
            getFormService().getForm(path, true);
        }
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of form '{0}'")
    public String dryValidationAndCleanup(String formPath, Map<String, Object> variables) {
        try {
            JsonNode data = getFormService()
                    .submission(formPath, false, toFormIoSubmissionData(variables))
                    .get("data");
            return unwrapSubformData(data, formPath).toString();
        } catch (BadRequestException bre) {
            throw new FormValidationException(getExceptionDetails(bre.getResponse()));
        }
    }

    public void uploadForm(String formDefinitionJson) {
        FormApi formApi = getFormService();
        formApi.createForm(formDefinitionJson);
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
        } catch (IOException ignored) {}
        return "";
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Checking if validation of form '{0}' should be skipped (submission state: '{1}')")
    public boolean shouldSkipValidation(String formKey, String submissionState) {
        String formDefinitionJson = getFormDefinition(formKey).toString();
        JSONArray submitButtons = getSubmitButtons(submissionState, formDefinitionJson);
        if (!submitButtons.isEmpty()) {
            Map<String, Object> actualSubmitButton = (Map<String, Object>) submitButtons.get(0);
            Map<String, Object> properties = (Map<String, Object>) actualSubmitButton.get("properties");
            return Boolean.parseBoolean(String.valueOf(properties.get("skipValidation")));
        } else {
            return false;
        }
    }

    private JSONArray getSubmitButtons(String submissionState, String formDefinitionJson) {
        return SUBMIT_BUTTONS_CACHE.computeIfAbsent(Objects.hash(submissionState, formDefinitionJson),
                inputState -> JsonPath.read(formDefinitionJson, String.format("$..components[?(@.state == '%s')]", submissionState)));
    }

    private JsonNode cleanUnusedData(String formPath, Map<String, Object> variables) {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        List<JsonNode> childComponentDefinitions = getChildComponentDefinitions(getFormDefinition(formPath));
        Map<String, Object> wrappedObjects = getWrappedVariables(convertedVariables, childComponentDefinitions);
        try {
            return getFormService()
                    .submission(formPath, true, toFormIoSubmissionData(wrappedObjects))
                    .get("data");
        } catch (BadRequestException bre) {
            throw new FormNotFoundException(formPath);
        }
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition of form '{0}'")
    public JsonNode getFormDefinition(String formPath) {
        return FORMS_CACHE.computeIfAbsent(formPath, path -> getFormService().getForm(path, true));
    }

    protected JsonNode getForm(String formPath) throws FormNotFoundException {
        try {
            return getFormService().getForm(formPath, true);
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

    protected static FormApi getFormService() {
        ResteasyWebTarget target = client.target(UriBuilder.fromPath(FORMIO_SERVER_PATH));
        return target.proxy(FormApi.class);
    }

    public static String toValidFormioIdentifier(String identifier) {
        final String invalidExclusivePattern = "[^a-zA-Z0-9-]";
        return identifier.replaceAll(invalidExclusivePattern, "-");
    }

    public static String toValidFormioPath(String path) {
        final String invalidExclusivePattern = "[^a-zA-Z0-9-/]";
        return path.replaceAll(invalidExclusivePattern, "-");
    }

    protected JsonNode unwrapSubformData(JsonNode data, String formPath) {
        JsonNode formDefinition = getFormDefinition(formPath);
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

    protected JsonNode wrapSubformData(JsonNode data, JsonNode definition) {
        if (data.isObject()) {
            return wrapSubformDataInObject(data, definition);
        }
        if (data.isArray()) {
            return wrapSubformDataInArray((ArrayNode)data, definition);
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

}
