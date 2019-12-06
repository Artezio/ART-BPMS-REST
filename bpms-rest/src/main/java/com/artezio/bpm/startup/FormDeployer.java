package com.artezio.bpm.startup;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.forms.FormClient;
import com.artezio.forms.formio.FormioClient;
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
    private final static boolean IS_FORM_VERSIONING_ENABLED = Boolean.parseBoolean(System.getProperty("FORM_VERSIONING", "true"));

    @Inject
    private DeploymentSvc deploymentSvc;
    @Inject
    private FormClient formioClient;

    @PostConstruct
    public void uploadForms() {
        if (deploymentSvc.deploymentsExist()) {
            List<String> formIds = deploymentSvc.listLatestDeploymentFormIds();
            formIds.stream()
                    .map(formId -> formId.endsWith(".json") ? formId.substring(0, formId.length() - 5) : formId)
                    .forEach(formId ->
                            formioClient.uploadForm(uploadNestedForms(getFormDefinition(deploymentSvc.getLatestDeploymentForm(formId)))));
        }
    }

    protected String uploadNestedForms(String formDefinition) {
        try {
            return uploadNestedForms(OBJECT_MAPPER.readTree(formDefinition)).toString();
        } catch (IOException e) {
            throw new RuntimeException("Unable to read form.", e);
        }
    }

    protected JsonNode uploadNestedForms(JsonNode definition) throws IOException {
        if (isNestedForm(definition) && !definition.get("path").asText().isEmpty()) {
            return uploadNestedForm(definition);
        }
        if (definition.isArray()) {
            return uploadNestedForms((ArrayNode) definition);
        }
        if (definition.isObject()) {
            return uploadNestedForms((ObjectNode) definition);
        }
        return definition;
    }

    protected JsonNode uploadNestedForms(ObjectNode node) throws IOException {
        node = node.deepCopy();
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        for (String fieldName : fieldNames) {
            JsonNode nodeWithReplacedIds = uploadNestedForms(node.get(fieldName));
            node.set(fieldName, nodeWithReplacedIds);
        }
        return node;
    }

    protected JsonNode uploadNestedForms(ArrayNode node) throws IOException {
        node = node.deepCopy();
        for (int i = 0; i < node.size(); i++) {
            JsonNode nodeWithReplacedIds = uploadNestedForms(node.get(i));
            node.set(i, nodeWithReplacedIds);
        }
        return node;
    }

    protected JsonNode uploadNestedForm(JsonNode referenceDefinition) throws IOException {
        String formPath = referenceDefinition.get("path").asText().substring(1);
        JsonNode formDefinition = OBJECT_MAPPER.readTree(getFormDefinition(referenceDefinition.toString()));
        JsonNode fullFormDefinition = OBJECT_MAPPER.readTree(getFormDefinition(deploymentSvc.getLatestDeploymentForm(formPath)));
        formioClient.uploadForm(uploadNestedForms(fullFormDefinition.toString()));
        return setNestedFormFields(formDefinition);
    }

    protected JsonNode setNestedFormFields(JsonNode referenceDefinition) throws IOException {
        ObjectNode modifiedNode = referenceDefinition.deepCopy();
        String formPath = referenceDefinition.get("path").asText();
        String id = formioClient.getFormId(formPath);
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
                for (String fieldName : fieldNames) {
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

    private String getFormDefinition(String form) {
        if (!IS_FORM_VERSIONING_ENABLED) {
            return form;
        }
        String latestDeploymentId = deploymentSvc.getLatestDeploymentId();
        try {
            ObjectNode formDefinition = (ObjectNode) OBJECT_MAPPER.readTree(form);
            formDefinition.put("path", formDefinition.get("path").asText() + "-" + latestDeploymentId);
            if (formDefinition.has("name")) {
                formDefinition.put("name", formDefinition.get("name").asText() + "-" + latestDeploymentId);
            }
            if (formDefinition.has("machineName")) {
                formDefinition.put("machineName", formDefinition.get("machineName").asText() + "-" + latestDeploymentId);
            }
            return formDefinition.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error while parsing json form definition", e);
        }
    }

}
