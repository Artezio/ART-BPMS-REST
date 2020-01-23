package com.artezio.forms.formio;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.bpm.services.VariablesMapper;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.exceptions.FormValidationException;
import com.artezio.forms.formio.jackson.ObjectMapperProvider;
import com.artezio.logging.Log;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.*;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import org.apache.commons.lang3.StringUtils;

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
import static java.util.AbstractMap.SimpleEntry;
import static java.util.Arrays.asList;

@Named
public class FormioClient implements FormClient {

    private final static Map<String, JsonNode> FORM_CACHE = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> SUBMISSION_PROCESSING_DECISIONS_CACHE = new ConcurrentHashMap<>();
    private final static String DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME = "dryValidationAndCleanUp.js";
    private final static String CLEAN_UP_SCRIPT_NAME = "cleanUp.js";
    private final static String GRID_NO_ROW_WRAPPING_PROPERTY = "noRowWrapping";
    private final static ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDefaultMergeable(false);
    private final static ParseContext JAYWAY_PARSER = JsonPath.using(Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .build());

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
            JsonNode form = getForm(deploymentId, formPath);
            JsonNode data = cleanUnusedData(deploymentId, formPath, variables);
            ((ObjectNode) form).set("data", wrapGridData(data, form));
            return form.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to get a form.", e);
        }
    }

    public boolean shouldProcessSubmission(String deploymentId, String formPath, String submissionState) {
        JsonNode formDefinition = getForm(deploymentId, formPath);
        String cacheKey = deploymentId + "-" + formPath + "-" + submissionState;
        return SUBMISSION_PROCESSING_DECISIONS_CACHE.computeIfAbsent(
                cacheKey,
                key -> shouldProcessSubmission(formDefinition, submissionState));
    }

    private JsonNode getForm(String deploymentId, String formPath) {
        String formPathWithExt = !formPath.endsWith(".json") ? formPath.concat(".json") : formPath;
        String cacheKey = deploymentId + "-" + formPath;
        JsonNode form = FORM_CACHE.computeIfAbsent(cacheKey,
                key -> {
                    try {
                        return JSON_MAPPER.readTree(deploymentSvc.getResource(deploymentId, formPathWithExt));
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to parse the form json.", e);
                    }
                });
        return expandSubforms(deploymentId, form);
    }

    private JsonNode cleanUnusedData(String deploymentId, String formPath, Map<String, Object> variables) throws IOException {
        Map<String, Object> convertedVariables = variablesMapper.convertEntitiesToMaps(variables);
        JsonNode formDefinition = getForm(deploymentId, formPath);
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
            if (hasTypeOf(component, "container") || isArrayComponent(component)) {
                return expandSubforms(deploymentId, component);
            } else if (hasTypeOf(component, "form")) {
                return convertToContainer(deploymentId, component);
            } else {
                return component;
            }
        };
    }

    private JsonNode convertToContainer(String deploymentId, JsonNode form) {
        String formResourceName = form.get("key").asText() + ".json";
        JsonNode container = convertToContainer(form);
        JsonNode components = getForm(deploymentId, formResourceName).get("components").deepCopy();
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

    private boolean shouldProcessSubmission(JsonNode form, String submissionState) {
        Filter saveStateComponentsFilter = Filter.filter((Criteria.where("action").eq("saveState").and("state").eq(submissionState)));
        return toStream(JAYWAY_PARSER.parse(form).read("$..components[?]", saveStateComponentsFilter))
                .map(component -> component.at("/properties/isSubmissionProcessed").asBoolean(true))
                .findFirst()
                .get();
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
            List<JsonNode> childComponents = getChildComponents(definition);
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

    protected List<JsonNode> getChildComponents(JsonNode definition) {
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
        JsonNode formDefinition = getForm(deploymentId, formPath);
        return unwrapGridData(data, formDefinition);
    }

    protected JsonNode unwrapGridData(JsonNode data, JsonNode definition) {
        if (hasChildComponents(definition)) {
            List<JsonNode> childComponents = getChildComponents(definition);
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

    protected boolean isContainerComponent(JsonNode componentDefinition) {
        return hasTypeOf(componentDefinition, "form")
                || hasTypeOf(componentDefinition, "container")
                || hasTypeOf(componentDefinition, "survey");
    }

    protected boolean isArrayComponent(JsonNode componentDefinition) {
        return hasTypeOf(componentDefinition, "datagrid")
                || hasTypeOf(componentDefinition, "editgrid");
    }

    protected boolean hasTypeOf(JsonNode component, String type) {
        JsonNode typeField = component.get("type");
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

    @Override
    public List<String> getFormVariableNames(String deploymentId, String formPath) {
        return Optional.ofNullable(getChildComponents(getForm(deploymentId, formPath)))
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(component -> component.path("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.path("key").asText()))
                .map(component -> component.get("key").asText())
                .collect(Collectors.toList());
    }

    @Log(level = CONFIG, beforeExecuteMessage = "Performing dry validation and cleanup of a form '{0}'")
    public String dryValidationAndCleanup(String deploymentId, String formPath, Map<String, Object> submittedVariables,
                                          Map<String, Object> currentVariables) {
        try {
            Map<String, Object> formVariables = getFormVariables(deploymentId, formPath, submittedVariables, currentVariables);
            String submissionData = JSON_MAPPER.writeValueAsString(toFormIoSubmissionData(formVariables));
            String formSource = getForm(deploymentId, formPath).toString();
            try (InputStream validationResult = nodeJsProcessor.executeScript(DRY_VALIDATION_AND_CLEANUP_SCRIPT_NAME, formSource, submissionData)) {
                JsonNode cleanData = JSON_MAPPER.readTree(validationResult).get("data");
                return unwrapGridData(cleanData, deploymentId, formPath).toString();
            }
        } catch (Exception ex) {
            throw new FormValidationException(ex);
        }
    }

    private Map<String, Object> getFormVariables(String deploymentId, String formPath, Map<String, Object> submittedVariables,
                                                 Map<String, Object> currentVariables) {
        return getFormVariables(getChildComponents(getForm(deploymentId, formPath)), submittedVariables, currentVariables);
    }

    private Map<String, Object> getFormVariables(List<JsonNode> formComponents, Map<String, Object> submittedVariables,
                                                 Map<String, Object> currentVariables) {
        return Optional.ofNullable(formComponents)
                .map(Collection::stream)
                .orElse(Stream.empty())
                .filter(component -> component.get("input").asBoolean())
                .filter(component -> StringUtils.isNotBlank(component.get("key").asText()))
                .map(component -> getFormVariable(component, submittedVariables, currentVariables))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map.Entry<String, Object> getFormVariable(JsonNode component, Map<String, Object> submittedVariables,
                                                      Map<String, Object> currentVariables) {
        if (isContainerComponent(component)) {
            return getContainerVariable(component, submittedVariables, currentVariables);
        } else if (isArrayComponent(component)) {
            return getArrayComponentVariable(component, submittedVariables, currentVariables);
        } else {
            return getSimpleComponentVariable(component, submittedVariables, currentVariables);
        }
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getContainerVariable(JsonNode component, Map<String, Object> submittedVariables,
                                                           Map<String, Object> currentVariables) {
        String componentKey = component.get("key").asText();
        submittedVariables = (Map<String, Object>) submittedVariables.get(componentKey);
        currentVariables = (Map<String, Object>) currentVariables.get(componentKey);
        Map<String, Object> containerValue = getFormVariables(getChildComponents(component), submittedVariables, currentVariables);
        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }

    @SuppressWarnings("unchecked")
    private Map.Entry<String, Object> getArrayComponentVariable(JsonNode component, Map<String, Object> submittedVariables,
                                                                Map<String, Object> currentVariables) {
        String componentKey = component.get("key").asText();
        List<Map<String, Object>> containerValue = new ArrayList<>();
        List<Map<String, Object>> editableArrayData = (List<Map<String, Object>>) submittedVariables.get(componentKey);
        List<Map<String, Object>> readOnlyDataArrayData = (List<Map<String, Object>>) currentVariables.get(componentKey);
        if (editableArrayData != null) {
            for (int i = 0; i < editableArrayData.size(); i++) {
                Map<String, Object> editableArrayItemData = editableArrayData.get(i);
                Map<String, Object> readOnlyDataArrayItemData = readOnlyDataArrayData.get(i);
                Map<String, Object> containerItemValue = getFormVariables(getChildComponents(component), editableArrayItemData, readOnlyDataArrayItemData);
               containerValue.add(containerItemValue);
           }
        }

        return containerValue.isEmpty()? null : new SimpleEntry<>(componentKey, containerValue);
    }

    private Map.Entry<String, Object> getSimpleComponentVariable(JsonNode component, Map<String, Object> editableData,
                                                                 Map<String, Object> readOnlyData) {
        String componentKey = component.get("key").asText();
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

        return !component.path("disabled").asBoolean() ? editableDataEntry : readOnlyDataEntry;
    }

}
