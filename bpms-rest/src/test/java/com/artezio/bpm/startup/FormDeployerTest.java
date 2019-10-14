package com.artezio.bpm.startup;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.formio.client.FormClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FormDeployerTest {
    @InjectMocks
    private FormDeployer formDeployer;
    @Mock
    private FormClient formClient;
    @Mock
    private DeploymentSvc deploymentSvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testUploadNestedForms() throws IOException {
        String formDefinition = IOUtils.toString(getClass().getClassLoader().getResource("full-form-with-nested-forms.json"), Charset.defaultCharset());
        String latestDeploymentIdSuffix = "latest-deployment-id";
        ObjectNode formDefinitionNode = (ObjectNode) objectMapper.readTree(formDefinition);
        ObjectNode nestedForm1DefinitionNode = (ObjectNode) formDefinitionNode.get("components").get(1);
        nestedForm1DefinitionNode.remove("form");
        String nestedForm1Definition = nestedForm1DefinitionNode.toString();
        ObjectNode nestedForm2DefinitionNode = (ObjectNode) nestedForm1DefinitionNode.get("components").get(1);
        nestedForm2DefinitionNode.remove("form");
        String nestedForm2Definition = nestedForm2DefinitionNode.toString();
        ObjectNode nestedArrayFormDefinitionNode = (ObjectNode) formDefinitionNode.get("components").get(2).get("components").get(0);
        nestedArrayFormDefinitionNode.remove("form");
        String nestedArrayFormDefinition = nestedArrayFormDefinitionNode.toString();
        when(formClient.getFormDefinition("/forms/nested-1-" + latestDeploymentIdSuffix)).thenReturn(nestedForm1DefinitionNode);
        when(formClient.getFormDefinition("/forms/nested-2-" + latestDeploymentIdSuffix)).thenReturn(nestedForm2DefinitionNode);
        when(formClient.getFormDefinition("/forms/nested-array-1-" + latestDeploymentIdSuffix)).thenReturn(nestedArrayFormDefinitionNode);
        when(deploymentSvc.getLatestDeploymentForm("forms/nested-1")).thenReturn(nestedForm1Definition);
        when(deploymentSvc.getLatestDeploymentForm("forms/nested-2")).thenReturn(nestedForm2Definition);
        when(deploymentSvc.getLatestDeploymentForm("forms/nested-array-1")).thenReturn(nestedArrayFormDefinition);
        when(deploymentSvc.getLatestDeploymentId()).thenReturn(latestDeploymentIdSuffix);

        JsonNode actual = formDeployer.uploadNestedForms(objectMapper.readTree(formDefinition));

        assertEquals("form", actual.at("/type").asText());
        assertFalse(actual.at("/components/1/form").isMissingNode());
        assertFalse(actual.at("/components/1/components/1/form").isMissingNode());
        assertFalse(actual.at("/components/1/components/1/disabled").isMissingNode());
        assertTrue(actual.at("/components/1/components/1/disabled").asBoolean());
    }

}
