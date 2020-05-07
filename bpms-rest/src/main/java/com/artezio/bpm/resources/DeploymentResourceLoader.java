package com.artezio.bpm.resources;

import org.apache.commons.collections4.map.LRUMap;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.RepositoryService;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeploymentResourceLoader extends AbstractResourceLoader {

    private static final String PROCESS_ENGINE_NAME = System.getenv("PROCESS_ENGINE_NAME");
    private static final int DEPLOYMENT_RESOURCES_CACHE_SIZE = Integer.parseInt(System.getProperty("DEPLOYMENT_RESOURCES_CACHE_SIZE", "1000"));
    private static final Map<String, byte[]> RESOURCES_CACHE = Collections.synchronizedMap(new LRUMap<>(DEPLOYMENT_RESOURCES_CACHE_SIZE));
    protected String deploymentId;

    public DeploymentResourceLoader(String deploymentId, String rootDir) {
        super(rootDir);
        this.deploymentId = deploymentId;
    }

    @Override
    public InputStream getResource(String resourceKey) {
        String cacheKey = String.format("%s.%s", deploymentId, resourceKey);
        byte[] resource = RESOURCES_CACHE.computeIfAbsent(cacheKey, key -> getResourceBytes(resourceKey));
        return new ByteArrayInputStream(resource);
    }

    private byte[] getResourceBytes(String resourceKey) {
        String resourcePath = String.format("%s/%s", rootDirectory, getResourcePath(resourceKey));
        resourcePath = resourcePath.replaceAll("[/]{2,}", "/");
        try(InputStream resource = getRepositoryService().getResourceAsStream(deploymentId, resourcePath)) {
            return resource.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> listResourceNames() {
        return getRepositoryService().getDeploymentResourceNames(deploymentId).stream()
                .filter(resourceName -> resourceName.startsWith(rootDirectory))
                .map(resourceName -> resourceName.substring((rootDirectory + "/").length()))
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

    @Override
    public String getGroupId() {
        return DEPLOYMENT_PROTOCOL + deploymentId;
    }

}
