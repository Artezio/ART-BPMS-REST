package com.artezio.bpm.startup;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.formio.client.FormClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Startup
@DependsOn("DefaultEjbProcessApplication")
public class FormDeployer {

    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    private DeploymentSvc deploymentSvc;
    @Inject
    private FormClient formClient;

    @PostConstruct
    public void uploadForms() {
        List<String> formIds = deploymentSvc.listFormIds();
        formIds.stream()
                .map(formId -> formId.endsWith(".json") ? formId.substring(0, formId.length() - 5) : formId)
                .forEach(formId ->
                        formClient.uploadFormIfNotExists(formId, uploadNestedForms(deploymentSvc.getForm(formId))));
    }

    protected String uploadNestedForms(String formDefinition) {
        try {
            return uploadNestedForms(OBJECT_MAPPER.readTree(formDefinition)).toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read form.", e);
        }
    }

    protected JsonNode uploadNestedForms(JsonNode definition) {
        if (isNestedForm(definition) && !definition.get("path").asText().isEmpty()) {
            return uploadNestedForm(definition);
        }
        if (definition.isArray()) {
            return uploadNestedForms((ArrayNode)definition);
        }
        if (definition.isObject()) {
            return uploadNestedForms((ObjectNode)definition);
        }
        return definition;
    }

    protected JsonNode uploadNestedForms(ObjectNode node) {
        node = node.deepCopy();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        for (String fieldName: fieldNames) {
            JsonNode nodeWithReplacedIds = uploadNestedForms(node.get(fieldName));
            node.set(fieldName, nodeWithReplacedIds);
        }
        return node;
    }

    protected JsonNode uploadNestedForms(ArrayNode node) {
        node = node.deepCopy();
        for (int i = 0; i < node.size(); i++) {
            JsonNode nodeWithReplacedIds = uploadNestedForms(node.get(i));
            node.set(i, nodeWithReplacedIds);
        }
        return node;
    }

    protected JsonNode uploadNestedForm(JsonNode referenceDefinition) {
        String formPath = referenceDefinition.get("path").asText().substring(1);
        formClient.uploadFormIfNotExists(formPath, uploadNestedForms(deploymentSvc.getForm(formPath)));
        return setNestedFormFields(referenceDefinition);
    }

    protected JsonNode setNestedFormFields(JsonNode referenceDefinition) {
        ObjectNode modifiedNode = referenceDefinition.deepCopy();
        String id = formClient.getFormDefinition(referenceDefinition.get("path").asText()).get("_id").asText();
        modifiedNode.put("form", id);
        modifiedNode.put("reference", false);
        modifiedNode.put("path", "");
        if (referenceDefinition.has("protected") && referenceDefinition.get("protected").asBoolean()) {
            modifiedNode.remove("protected");
            referenceDefinition = disableAllFields(modifiedNode);
            return uploadNestedForms(referenceDefinition);
        } else {
            return uploadNestedForms(modifiedNode);
        }
    }

    protected JsonNode disableAllFields(JsonNode node) {
        if (node.isArray()) {
            ArrayNode arrayNode = node.deepCopy();
            for (int index = 0; index < node.size(); index++) {
                arrayNode.set(index, disableAllFields(node.get(index)));
            }
            return arrayNode;
        }
        if (node.isObject()) {
            ObjectNode objectNode = node.deepCopy();
            if (objectNode.has("type")) {
                objectNode.set("disabled", BooleanNode.TRUE);
            }
            if (objectNode.isContainerNode()) {
                List<String> fieldNames = new ArrayList<>();
                node.fieldNames().forEachRemaining(fieldNames::add);
                for (String fieldName: fieldNames) {
                    JsonNode nodeWithDisabledFields = disableAllFields(node.get(fieldName));
                    objectNode.set(fieldName, nodeWithDisabledFields);
                }
            }
            return objectNode;
        }
        return node;
    }

    protected boolean isNestedForm(JsonNode node) {
        return node.isContainerNode()
                && node.has("form")
                && node.has("type")
                && node.get("type").asText().equals("form");
    }

}
