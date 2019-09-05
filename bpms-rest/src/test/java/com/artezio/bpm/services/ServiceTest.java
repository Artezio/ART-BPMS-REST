package com.artezio.bpm.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.tika.Tika;
import org.camunda.bpm.engine.*;
import org.camunda.bpm.engine.history.HistoricTaskInstance;
import org.camunda.bpm.engine.history.HistoricVariableInstance;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.repository.DeploymentBuilder;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.variable.impl.value.builder.FileValueBuilderImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.Rule;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

abstract public class ServiceTest {
    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    static protected File getFile(String fileName) {
        return new File(ServiceTest.class.getClassLoader().getResource(fileName).getFile());
    }

    List<ResourceEntity> getDeploymentResources(String deploymentId) {
        return getRepositoryService().getDeploymentResources(deploymentId)
                .stream()
                .map(resource -> (ResourceEntity) resource)
                .collect(Collectors.toList());
    }

    List<Deployment> getDeploymentList() {
        return getRepositoryService().createDeploymentQuery().list();
    }

    protected Deployment createDeployment(String deploymentName, String... fileNames) throws IOException {
        RepositoryService repositoryService = getRepositoryService();
        DeploymentBuilder deploymentBuilder = repositoryService.createDeployment().name(deploymentName);
        for (String fileName : fileNames) {
            File file = getFile(fileName);
            try (FileInputStream in = new FileInputStream(file)) {
                deploymentBuilder.addInputStream(file.getName(), in);
            } catch (IOException e) {
                throw e;
            }
        }
        return deploymentBuilder.deploy();
    }

    User createUser(String id) {
        IdentityService identityService = getIdentityService();
        User user = identityService.newUser(id);
        identityService.saveUser(user);
        return user;
    }

    Group createGroup(String id, String type) {
        IdentityService identityService = getIdentityService();
        Group group = identityService.newGroup(id);
        group.setType(type);
        identityService.saveGroup(group);
        return group;
    }

    void addUserToGroup(User user, Group group) {
        IdentityService identityService = getIdentityService();
        identityService.createMembership(user.getId(), group.getId());
    }

    void addUserToStarterGroup(User user, Group group) {
    }

    IdentityService getIdentityService() {
        return processEngineRule.getIdentityService();
    }

    protected RepositoryService getRepositoryService() {
        return processEngineRule.getRepositoryService();
    }

    FormService getFormService() {
        return processEngineRule.getFormService();
    }

    protected RuntimeService getRuntimeService() {
        return processEngineRule.getRuntimeService();
    }

    Map<String, Object> getVariablesFromHistoryService(String taskId) {
        HistoricTaskInstance historicTaskInstance = getHistoryService().createHistoricTaskInstanceQuery().taskId(taskId).singleResult();
        return getHistoryService().createHistoricVariableInstanceQuery()
                .taskIdIn(historicTaskInstance.getId())
                .list()
                .stream()
                .collect(Collectors.toMap(HistoricVariableInstance::getName, HistoricVariableInstance::getValue));
    }

    void setVariablesToTask(String taskId, Map<String, Object> variables) {
        getTaskService().setVariables(taskId, variables);
    }

    HistoryService getHistoryService() {
        return processEngineRule.getHistoryService();
    }

    TaskService getTaskService() {
        return processEngineRule.getTaskService();
    }

    Task createTask(String taskId, String assignee, String candidateUserId, List<String> candidateGroups) {
        TaskService taskService = getTaskService();
        Task task = taskService.newTask(taskId);
        task.setAssignee(assignee);
        taskService.saveTask(task);
        taskService.addCandidateUser(taskId, candidateUserId);
        candidateGroups.forEach(group ->taskService.addCandidateGroup(taskId, group));
        return task;
    }

    void assertFileValueEquals(FileValue expected, FileValue actual) throws IOException {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getFilename(), actual.getFilename());
        assertEquals(expected.getMimeType(), actual.getMimeType());
        if (expected.getValue() != null && actual.getValue() != null) {
            assertArrayEquals(IOUtils.toByteArray(expected.getValue()), IOUtils.toByteArray(actual.getValue()));
        } else {
            assertEquals(expected.getValue(), actual.getValue());
        }
    }

    void assertFileResponseEquals(Response expected, Response actual) {
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getMediaType(), actual.getMediaType());
        assertArrayEquals((byte[]) expected.getEntity(), (byte[]) actual.getEntity());
    }

    String getFileValueRepresentationJson(Map<String, Object> fileValue) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(fileValue);
    }

    Map<String, Object> getFileAsAttributesMap(File file) throws IOException {
        String mimeType = new Tika().detect(file);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        String base64EncodedFileContent = Base64.getMimeEncoder().encodeToString(fileContent);
        Map<String, Object> fileValue = new HashMap<>();
        fileValue.put("name", file.getName());
        fileValue.put("originalName", file.getName());
        fileValue.put("size", (int) file.length());
        fileValue.put("storage", "base64");
        fileValue.put("type", mimeType);
        fileValue.put("url", "data:" + mimeType + ";base64," + base64EncodedFileContent);

        return fileValue;
    }

    FileValue getFileValue(File file) throws IOException {
        String mimeType = new Tika().detect(file);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return new FileValueBuilderImpl(file.getName())
                .encoding(Charset.forName("UTF-8"))
                .mimeType(mimeType)
                .file(fileContent).create();
    }

    byte[] getFileContentFromUrl(String url) {
        String base64EncodedFileContent = extractFileContentFromUrl(url);
        return decodeFromBase64(base64EncodedFileContent);
    }

    String extractFileContentFromUrl(String url) {
        return url.split(",")[1];
    }

    byte[] decodeFromBase64(String encodedString) {
        return Base64.getMimeDecoder().decode(encodedString);
    }

}
