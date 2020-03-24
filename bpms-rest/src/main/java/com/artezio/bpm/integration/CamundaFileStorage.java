package com.artezio.bpm.integration;

import com.artezio.forms.FileStorage;
import com.artezio.forms.FileStorageEntity;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;

import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.Map;

public class CamundaFileStorage implements FileStorage {

    private static final String PROCESS_ENGINE_NAME = System.getProperty("PROCESS_ENGINE_NAME", ProcessEngines.NAME_DEFAULT);
    private static final String CAMUNDA_STORAGE_NAME = "url";

    private String taskId;
    private Map<String, Object> variables;
    private ProcessEngine processEngine;
    private ServletContext servletContext;

    public CamundaFileStorage(String taskId) {
        this.taskId = taskId;
        variables = new HashMap<>();
    }

    public CamundaFileStorage(Map<String, Object> variables) {
        this.taskId = "";
        this.variables = variables;
    }

    @Override
    public void store(FileStorageEntity fileStorageEntity) {
        FileValue fileValue = createFileValue(fileStorageEntity);
        variables.put(fileStorageEntity.getId(), fileValue);
        changeStorageType(fileStorageEntity);
    }

    @Override
    public FileStorageEntity retrieve(String id) {
        TaskService taskService = getProcessEngine().getTaskService();
        FileValue fileValue = taskService.getVariableTyped(taskId, id);
        return new CamundaFileStorageEntity(id, fileValue);
    }

    @Override
    public String getDownloadUrlPrefix() {
        return String.format("%s/api/task/%s", getServletContext().getContextPath(), taskId);
    }

    private FileValue createFileValue(FileStorageEntity fileStorageEntity) {
        return Variables.fileValue(fileStorageEntity.getName())
                .mimeType(fileStorageEntity.getMimeType())
                .file(fileStorageEntity.getContent())
                .create();
    }

    private void changeStorageType(FileStorageEntity fileStorageEntity) {
        fileStorageEntity.setUrl("file/" + fileStorageEntity.getId());
        fileStorageEntity.setStorage(CAMUNDA_STORAGE_NAME);
    }

    private ProcessEngine getProcessEngine() {
        if (processEngine == null) {
            ProcessEngine defaultProcessEngine = BpmPlatform.getProcessEngineService().getProcessEngine(PROCESS_ENGINE_NAME);
            processEngine = defaultProcessEngine != null
                    ? defaultProcessEngine
                    : ProcessEngines.getProcessEngine(PROCESS_ENGINE_NAME);
        }
        return processEngine;
    }

    private ServletContext getServletContext() {
        if (servletContext == null) {
            servletContext = CDI.current().select(ServletContext.class).get();
        }
        return servletContext;
    }

}
