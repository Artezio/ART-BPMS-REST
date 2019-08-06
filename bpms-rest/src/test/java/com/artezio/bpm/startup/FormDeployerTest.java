package com.artezio.bpm.startup;

import com.artezio.bpm.services.DeploymentSvc;
import com.artezio.formio.client.FormClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.Charset;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class FormDeployerTest {
    @InjectMocks
    private FormDeployer formDeployer;
    @Mock
    private FormClient formClient;
    @Mock
    private DeploymentSvc deploymentSvc;

    @Test
    public void testUploadNestedForms() throws IOException {
        String formDefinition = IOUtils.toString(getClass().getClassLoader().getResource("full-form-with-nested-forms.json"), Charset.defaultCharset());
        when(formClient.getFormDefinition("/forms/nested-1")).thenReturn(new ObjectMapper().readTree("{\"_id\": \"nested-1\"}"));
        when(formClient.getFormDefinition("/forms/nested-2")).thenReturn(new ObjectMapper().readTree("{\"_id\": \"nested-2\"}"));
        when(formClient.getFormDefinition("/forms/nested-array-1")).thenReturn(new ObjectMapper().readTree("{\"_id\": \"nested-array-form\"}"));
        when(deploymentSvc.getForm(anyString())).thenReturn("{}");

        JsonNode actual = formDeployer.uploadNestedForms(new ObjectMapper().readTree(formDefinition));

        assertEquals("form", actual.at("/type").asText());
        assertFalse(actual.at("/components/1/form").isMissingNode());
        assertEquals("nested-1", actual.at("/components/1/form").asText());
        assertFalse(actual.at("/components/1/components/1/form").isMissingNode());
        assertEquals("nested-2", actual.at("/components/1/components/1/form").asText());
        assertFalse(actual.at("/components/1/components/1/disabled").isMissingNode());
        assertTrue(actual.at("/components/1/components/1/disabled").asBoolean());
    }

}
