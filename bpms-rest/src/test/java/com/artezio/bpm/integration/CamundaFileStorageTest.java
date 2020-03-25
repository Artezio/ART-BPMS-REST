package com.artezio.bpm.integration;

import com.artezio.forms.FileStorageEntity;
import org.camunda.bpm.BpmPlatform;
import org.camunda.bpm.ProcessEngineService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngines;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({BpmPlatform.class, CDI.class})
public class CamundaFileStorageTest {

    private static final ProcessEngine processEngine = mock(ProcessEngine.class);

    private String fileName = "file.txt";
    private String mimeType = "text/plain";

    @Before
    public void init() {
        PowerMockito.mockStatic(BpmPlatform.class);
        ProcessEngineService processEngineService = mock(ProcessEngineService.class);
        when(BpmPlatform.getProcessEngineService()).thenReturn(processEngineService);
        when(processEngineService.getProcessEngine(ProcessEngines.NAME_DEFAULT)).thenReturn(processEngine);
    }

    @Test
    public void testStore() throws IOException {
        byte[] content = {1,2,3,4,5};
        String fileId = "fileId";
        Map<String, Object> variables = new HashMap<>();
        CamundaFileStorage fileStorage = new CamundaFileStorage(variables);
        FileValue fileValue = createFileValue(content);
        FileStorageEntity entity = new CamundaFileStorageEntity(fileId, fileValue);

        fileStorage.store(entity);

        assertFalse(variables.isEmpty());
        assertTrue(variables.containsKey(fileId));
        FileValue actual = (FileValue) variables.get(fileId);
        assertEquals(mimeType, actual.getMimeType());
        assertEquals(fileName, actual.getFilename());
        assertEquals("url", entity.getStorage());
        assertEquals("file/" + fileId, entity.getUrl());
        byte[] actualContent = actual.getValue().readAllBytes();
        assertArrayEquals(content, actualContent);
    }

    @Test
    public void testRetrieve() throws IOException {
        String taskId = "taskId";
        String fileId = "fileId";
        byte[] content = {1,2,3,4,5};
        CamundaFileStorage fileStorage = new CamundaFileStorage(taskId);
        TaskService taskService = mock(TaskService.class);
        FileValue fileValue = createFileValue(content);
        FileStorageEntity expected = new CamundaFileStorageEntity(fileId, fileValue);

        when(processEngine.getTaskService()).thenReturn(taskService);
        when(taskService.getVariableTyped(taskId, fileId)).thenReturn(fileValue);

        FileStorageEntity actual = fileStorage.retrieve(fileId);

        assertEquals(expected.getMimeType(), actual.getMimeType());
        assertEquals(expected.getName(), actual.getName());
        assertArrayEquals(expected.getContent().readAllBytes(), actual.getContent().readAllBytes());
    }

    @Test
    public void testGetDownloadUrlPrefix() {
        String taskId = "taskId";
        CamundaFileStorage fileStorage = new CamundaFileStorage(taskId);
        StringBuffer requestUrl = new StringBuffer(String.format("http://localhost:8080/bpms-rest/api/task/%s/", taskId));
        String expected = requestUrl.substring(0, requestUrl.lastIndexOf("/"));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        PowerMockito.mockStatic(CDI.class);
        CDI cdi = mock(CDI.class);
        Instance<HttpServletRequest> servletRequestInstance = mock(Instance.class);
        when(CDI.current()).thenReturn(cdi);
        when(cdi.select(HttpServletRequest.class)).thenReturn(servletRequestInstance);
        when(servletRequestInstance.get()).thenReturn(servletRequest);
        when(servletRequest.getRequestURL()).thenReturn(requestUrl);

        String actual = fileStorage.getDownloadUrlPrefix();

        assertEquals(expected, actual);
    }

    private FileValue createFileValue(byte[] content) {
        return Variables.fileValue(fileName)
                .mimeType(mimeType)
                .file(content)
                .create();
    }

}
