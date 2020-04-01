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
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.variable.impl.value.builder.FileValueBuilderImpl;
import org.camunda.bpm.engine.variable.value.FileValue;
import org.junit.Rule;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

abstract public class ServiceTest {

    protected static final String PUBLIC_RESOURCES_DIRECTORY = "public";

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule();

    static protected File getFile(String fileName) throws URISyntaxException {
        return new File(ServiceTest.class.getClassLoader().getResource(fileName).toURI());
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

    protected Deployment createDeployment(String deploymentName, String... fileNames) throws IOException, URISyntaxException {
        RepositoryService repositoryService = getRepositoryService();
        DeploymentBuilder deploymentBuilder = repositoryService.createDeployment().name(deploymentName);
        for (String fileName : fileNames) {
            File file = getFile(fileName);
            try (FileInputStream in = new FileInputStream(file)) {
                deploymentBuilder.addInputStream(fileName, in);
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

    protected ManagementService getManagementService() {
        return processEngineRule.getManagementService();
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
        candidateGroups.forEach(group -> taskService.addCandidateGroup(taskId, group));
        return task;
    }

    Task createTask(String taskId, String assignee, String candidateUserId, List<String> candidateGroups, Date dueDate, Date followUpDate) {
        Task task = createTask(taskId, assignee, candidateUserId, candidateGroups);
        task.setDueDate(dueDate);
        task.setFollowUpDate(followUpDate);
        getTaskService().saveTask(task);
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

    void assertFileResponseEquals(Response expected, Response actual) throws IOException {
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getMediaType(), actual.getMediaType());
        byte[] expectedBytes = IOUtils.toByteArray((InputStream) expected.getEntity());
        byte[] actualBytes = IOUtils.toByteArray((InputStream) actual.getEntity());
        assertArrayEquals(expectedBytes, actualBytes);
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
        fileValue.put("filename", file.getName());
        fileValue.put("size", (int) file.length());
        fileValue.put("mimeType", mimeType);
        fileValue.put("url", "data:" + mimeType + ";base64," + base64EncodedFileContent);

        return fileValue;
    }

    FileValue getFileValue(File file) throws IOException {
        String mimeType = new Tika().detect(file);
        byte[] fileContent = Files.readAllBytes(file.toPath());
        return new FileValueBuilderImpl(file.getName())
                .encoding(StandardCharsets.UTF_8)
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
    
    protected ProcessDefinition getLastProcessDefinition(String processDefinitionKey) {
        return getRepositoryService().createProcessDefinitionQuery()
                .latestVersion()
                .processDefinitionKey(processDefinitionKey)
                .singleResult();
    }

}
