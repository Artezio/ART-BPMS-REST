package com.artezio.bpm.resources;

import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.*;

public class DeploymentResourceLoaderTest {

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    @Test
    @Deployment(resources = {"forms/formWithState.json"})
    public void testGetResource() throws IOException {
        ResourceLoader loader = new DeploymentResourceLoader(getLatestDeploymentId());
        InputStream actual = loader.getResource("embedded:deployment:forms/formWithState.json");

        assertNotNull(actual);
        assertTrue(actual.available() > 0);
    }

    @Test
    @Deployment(resources = {"forms/formWithState.json", "forms/formWithSubform.json"})
    public void testListResources() {
        ResourceLoader loader = new DeploymentResourceLoader(getLatestDeploymentId());
        List<String> actuals = loader.listResourceNames("");

        assertEquals(2, actuals.size());
        assertTrue(actuals.contains("forms/formWithState.json"));
        assertTrue(actuals.contains("forms/formWithSubform.json"));
    }

    private String getLatestDeploymentId() {
        return processEngineRule.getRepositoryService()
                .createDeploymentQuery()
                .list()
                .stream()
                .min(Comparator.comparing(org.camunda.bpm.engine.repository.Deployment::getDeploymentTime))
                .get()
                .getId();
    }

}
