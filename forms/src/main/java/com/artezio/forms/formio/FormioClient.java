package com.artezio.forms.formio;

import com.artezio.forms.FormClient;
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
import org.apache.commons.io.IOUtils;
import org.camunda.bpm.engine.RepositoryService;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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

    private final static Map<String, JsonNode> FORM_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, JSONArray> FILE_FIELDS_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> SUBMISSION_PROCESSING_DECISIONS_CACHE = new ConcurrentHashMap<>();
    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "dryValidationAndCleanUp.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";
    private final static String GRID_NO_ROW_WRAPPING_PROPERTY = "noRowWrapping";

    private final static ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);

    @Inject
    private RepositoryService repositoryService;
    @Inject
    private NodeJsProcessor nodeJsProcessor;
    @Inject
    private FileAttributeConverter fileAttributeConverter;

    @Override
    @Log(level = CONFIG, beforeExecuteMessage = "Getting definition with data for a form '{0}'")
    public String getFormWithData(String formPath, String versionId, ObjectNode taskData) {
        try {
            JsonNode form = getForm(formPath, versionId);
            JsonNode cleanData = cleanUnusedData(formPath, versionId, taskData);
            JsonNode data = wrapGridData(cleanData, form);
            ((ObjectNode) form).set("data", data);
            return form.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
    }

    @Override
    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String formPath, String versionId, ObjectNode submittedData) {
        try {
            String formDefinition = getForm(formPath, versionId).toString();
            String formioSubmissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(submittedData));
            try (InputStream validationResult = nodeJsProcessor.executeScript(
                    DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME,
                    formDefinition,
                    formioSubmissionData)) {
                JsonNode cleanData = JSON_MAPPER.readTree(validationResult).get("data");
                JsonNode unwrappedCleanData = unwrapGridData(cleanData, formPath, versionId);
                return convertFilesInData(formDefinition, (ObjectNode)unwrappedCleanData, fileAttributeConverter::toCamundaFile).toString();
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while dry validation and cleanup", e);
        }
    }

    @Override
    public boolean shouldProcessSubmission(String formPath, String versionId, String submissionState) {
        String formDefinition = getForm(formPath, versionId).toString();
        String cacheKey = String.format("%s-%s-%s", versionId, formPath, submissionState);
        return SUBMISSION_PROCESSING_DECISIONS_CACHE.computeIfAbsent(
                cacheKey,
                key -> shouldProcessSubmission(formDefinition, submissionState));
    }

    private JsonNode getForm(String formPath, String deploymentId) {
        String formPathWithExt = !formPath.endsWith(".json") ? formPath.concat(".json") : formPath;
        String cacheKey = String.format("%s-%s", deploymentId, formPath);
        return FORM_CACHE.computeIfAbsent(
                cacheKey,
                key -> {
                    try {
                        JsonNode form = JSON_MAPPER.readTree(repositoryService.getResourceAsStream(deploymentId, formPathWithExt));
                        return expandSubforms(form, deploymentId);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to get the form into the cache.", e);
                    }
                });
    }

    private JsonNode cleanUnusedData(String formPath, String versionId, ObjectNode taskData) throws IOException {
        JsonNode formDefinition = getForm(formPath, versionId);
        ObjectNode formioSubmissionData = toFormIoSubmissionData(taskData);
        formioSubmissionData = convertFilesInData(formDefinition.toString(), formioSubmissionData, fileAttributeConverter::toFormioFile);
        String submissionData = JSON_MAPPER.writeValueAsString(formioSubmissionData);
        try (InputStream cleanUpResult = nodeJsProcessor.executeScript(CLEAN_UP_SCRIPT_NAME, formDefinition.toString(), submissionData)) {
            byte[] result = IOUtils.toByteArray(cleanUpResult);
            ObjectNode cleanData = (ObjectNode) JSON_MAPPER.readTree(result).get("data");
            return cleanData;
        }
    }

    protected JsonNode expandSubforms(JsonNode form, String deploymentId) {
        Collector<JsonNode, ArrayNode, ArrayNode> arrayNodeCollector = Collector
                .of(JSON_MAPPER::createArrayNode, ArrayNode::add, ArrayNode::addAll);
        Function<JsonNode, JsonNode> subformExpandFunction = getSubformExpandFunction(deploymentId);
        JsonNode components = toStream(form.get("components"))
                .map(subformExpandFunction)
                .collect(arrayNodeCollector);
        return ((ObjectNode) form).set("components", components);
    }

    private Function<JsonNode, JsonNode> getSubformExpandFunction(String deploymentId) {
        return component -> {
            if (isTypeOf(component, "container") || isArrayComponent(component)) {
                return expandSubforms(component, deploymentId);
            } else if (isFormComponent(component)) {
                return convertToContainer(component, deploymentId);
            } else {
                return component;
            }
        };
    }

    private JsonNode convertToContainer(JsonNode form, String deploymentId) {
        String formResourceName = form.get("key").asText() + ".json";
        JsonNode container = convertToContainer(form);
        JsonNode components = getForm(formResourceName, deploymentId).get("components").deepCopy();
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

    protected ObjectNode toFormIoSubmissionData(ObjectNode data) {
        if (!data.has("data")) {
            ObjectNode formioData = JSON_MAPPER.createObjectNode();
            formioData.set("data", data);
            return formioData;
        } else {
            return data;
        }
    }

    protected JsonNode unwrapGridData(JsonNode data, String formPath, String versionId) {
        JsonNode formDefinition = getForm(formPath, versionId);
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

    protected ObjectNode convertFilesInData(String formDefinition, ObjectNode data, Function<ObjectNode, ObjectNode> converter) {
        String rootObjectAttributePath = "";
        return convertFilesInData(rootObjectAttributePath, data, formDefinition, converter);
    }

    protected ObjectNode convertFilesInData(
            String objectAttributePath,
            ObjectNode objectVariableAttributes,
            String formDefinition,
            Function<ObjectNode, ObjectNode> converter) {
        ObjectNode convertedObject = JSON_MAPPER.createObjectNode();
        objectVariableAttributes.fields().forEachRemaining(
                attribute -> {
                    JsonNode attributeValue = attribute.getValue();
                    String attributeName = attribute.getKey();
                    String attributePath = !objectAttributePath.isEmpty()
                            ? objectAttributePath + "/" + attributeName
                            : attributeName;
                    if (isFileVariable(attributeName, formDefinition)) {
                        attributeValue = convertFilesInData((ArrayNode)attributeValue, converter);
                    } else if (attributeValue.isObject()) {
                        attributeValue = convertFilesInData(attributePath, (ObjectNode)attributeValue, formDefinition, converter);
                    } else if (attributeValue.isArray()) {
                        attributeValue = convertFilesInData(attributePath, (ArrayNode)attributeValue, formDefinition, converter);
                    }
                    convertedObject.set(attributeName, attributeValue);
                });
        return convertedObject;
    }

    private ArrayNode convertFilesInData(
            String attributePath,
            ArrayNode array,
            String formDefinition,
            Function<ObjectNode, ObjectNode> converter) {
        ArrayNode convertedArray = JSON_MAPPER.createArrayNode();
        StreamSupport.stream(array.spliterator(), false)
                .map(element -> {
                    if (element.isObject()) {
                        return convertFilesInData(attributePath, (ObjectNode) element, formDefinition, converter);
                    } else if (element.isArray()) {
                        return convertFilesInData(attributePath + "[*]", (ArrayNode)element, formDefinition, converter);
                    } else {
                        return element;
                    }
                })
                .forEach(convertedArray::add);
        return convertedArray;
    }

    private ArrayNode convertFilesInData(ArrayNode fileData, Function<ObjectNode, ObjectNode> converter) {
        ArrayNode convertedFileData = JSON_MAPPER.createArrayNode();
        StreamSupport.stream(fileData.spliterator(), false)
                .map(jsonNode -> (ObjectNode)jsonNode)
                .map(converter)
                .forEach(convertedFileData::add);
        return convertedFileData;
    }

    private boolean isFileVariable(String variableName, String formDefinition) {
        Function<String, JSONArray> fileFieldSearch = key -> JsonPath.read(formDefinition, String.format("$..[?(@.type == 'file' && @.key == '%s')]", variableName));
        JSONArray fileField = FILE_FIELDS_CACHE.computeIfAbsent(formDefinition + "." + variableName, fileFieldSearch);
        return !fileField.isEmpty();
    }

}
