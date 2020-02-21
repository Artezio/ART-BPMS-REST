package com.artezio.bpm.resources;

import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RepositoryService;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeploymentResourceLoader extends AbstractResourceLoader {

    private final static String PROCESS_ENGINE_NAME = System.getenv("PROCESS_ENGINE_NAME");
    protected String deploymentId;

    public DeploymentResourceLoader(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    @Override
    public InputStream getResource(String resourceKey) {
        resourceKey = getResourcePath(resourceKey);
        return getRepositoryService().getResourceAsStream(deploymentId, resourceKey);
    }

    @Override
    public List<String> listResourceNames(String initialPath) {
        return getRepositoryService().getDeploymentResourceNames(deploymentId).stream()
                .filter(resourceName -> resourceName.startsWith(initialPath))
                .collect(Collectors.toList());
    }

    private RepositoryService getRepositoryService() {
        return getProcessEngine().getRepositoryService();
    }

    /**
     * Extracted from
     * https://github.com/camunda/camunda-bpm-platform/blob/master/engine-rest/engine-rest/src/main/java/org/camunda/bpm/engine/rest/impl/application/ContainerManagedProcessEngineProvider.java
     * Changes are: added engine name
     */
    private ProcessEngine getProcessEngine() {
        String processEngineName = Optional.ofNullable(PROCESS_ENGINE_NAME).orElse(ProcessEngines.NAME_DEFAULT);
        ProcessEngine defaultProcessEngine = BpmPlatform.getProcessEngineService().getProcessEngine(processEngineName);
        return defaultProcessEngine != null
                ? defaultProcessEngine
                : ProcessEngines.getProcessEngine(processEngineName);
    }

}
