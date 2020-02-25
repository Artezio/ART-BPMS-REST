package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.repository.ProcessDefinitionRepresentation;
import com.artezio.bpm.rest.dto.task.FormDto;
import com.artezio.bpm.services.exceptions.NotAuthorizedException;
import com.artezio.bpm.validation.VariableValidator;
import junitx.framework.ListAssert;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.repository.Deployment;
import org.camunda.bpm.engine.runtime.Execution;
import org.camunda.bpm.engine.task.IdentityLink;
import org.camunda.bpm.engine.task.IdentityLinkType;
import org.easymock.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static junit.framework.TestCase.*;
import static org.easymock.EasyMock.*;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"javax.net.ssl.*"})
public class ProcessDefinitionSvcTest extends ServiceTest {

    private final String TEST_USER_ID = "testUser";
    private final String TEST_GROUP_ID = "testGroup";
    private final String ANOTHER_USER_ID = "anotherUser";

    @Mock
    private IdentitySvc identitySvc;
    @Mock
    private FormSvc formSvc;
    @Mock
    private VariablesMapper variablesMapper;
    @Mock
    private TaskSvc taskSvc;
    @Mock
    private VariableValidator variableValidator;
    @TestSubject
    @InjectMocks
    private ProcessDefinitionSvc processDefinitionSvc = new ProcessDefinitionSvc();

    @Before
    public void init() throws NoSuchFieldException {
        EasyMockSupport.injectMocks(this);
        Field repositoryServiceField = processDefinitionSvc.getClass().getDeclaredField("repositoryService");
        Field camundaFormServiceField = processDefinitionSvc.getClass().getDeclaredField("camundaFormService");
        Field runtimeServiceField = processDefinitionSvc.getClass().getDeclaredField("runtimeService");
        Field taskServiceField = processDefinitionSvc.getClass().getDeclaredField("taskService");
        setField(processDefinitionSvc, repositoryServiceField, getRepositoryService());
        setField(processDefinitionSvc, runtimeServiceField, getRuntimeService());
        setField(processDefinitionSvc, camundaFormServiceField, getFormService());
        setField(processDefinitionSvc, taskServiceField, taskSvc);
    }

    @After
    public void tearDown() {
        IdentityService identityService = getIdentityService();
        List<Deployment> deploymentList = getRepositoryService().createDeploymentQuery().list();
        deploymentList.forEach(deployment -> getRepositoryService().deleteDeployment(deployment.getId(), true));
        identityService.createUserQuery().list()
                .stream()
                .map(User::getId)
                .forEach(identityService::deleteUser);
        identityService.createGroupQuery().list()
                .stream()
                .map(Group::getId)
                .forEach(identityService::deleteGroup);
    }

    @Test
    public void testListStartableByUser() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-not-startable-by-anyone.bpmn");

        List<ProcessDefinitionRepresentation> startableProcesses = processDefinitionSvc.listStartableByUser();

        assertTrue(startableProcesses.isEmpty());
    }

    @Test
    public void testListStartableByUser_userHasNoAccess() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testUser.bpmn");
        expect(identitySvc.userId()).andReturn(ANOTHER_USER_ID);
        EasyMock.replay(identitySvc);

        List<ProcessDefinitionRepresentation> startableProcesses = processDefinitionSvc.listStartableByUser();

        EasyMock.verify(identitySvc);
        assertTrue(startableProcesses.isEmpty());
    }

    @Test
    public void testListStartableByUser_userIsCandidate() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testUser.bpmn");
        expect(identitySvc.userId()).andReturn(TEST_USER_ID);
        EasyMock.replay(identitySvc);

        List<ProcessDefinitionRepresentation> startableProcesses = processDefinitionSvc.listStartableByUser();

        EasyMock.verify(identitySvc);
        assertFalse(startableProcesses.isEmpty());
    }

    @Test
    public void testListStartableByUser_userIsInCandidateGroup() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testGroup.bpmn");
        expect(identitySvc.userGroups()).andReturn(asList(TEST_GROUP_ID));
        EasyMock.replay(identitySvc);

        List<ProcessDefinitionRepresentation> startableProcesses = processDefinitionSvc.listStartableByUser();

        EasyMock.verify(identitySvc);
        assertFalse(startableProcesses.isEmpty());
    }

    @Test
    public void testStart() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testGroup.bpmn");

        expect(taskSvc.getNextAssignedTask(anyObject(String.class))).andReturn(null);
        expect(identitySvc.userId()).andReturn(TEST_USER_ID);
        expect(identitySvc.userGroups()).andReturn(asList(TEST_GROUP_ID));
        replay(taskSvc, identitySvc);
        processDefinitionSvc.start("testProcessStartableByTestGroup", new HashMap<>());

        verify(taskSvc);
        long instanceCount = getRuntimeService()
                .createProcessInstanceQuery()
                .count();
        assertTrue(instanceCount > 0);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testStart_notAuthorized() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testUser.bpmn");
        expect(identitySvc.userId()).andReturn(ANOTHER_USER_ID);
        replay(identitySvc);

        processDefinitionSvc.start("testProcessStartableByTestUser", emptyMap());
    }

    @Test
    public void testStart_withStartForm() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-with-start-form.bpmn");
        String validatedVariablesJson = "{\"stringFormVariable\":\"non-default-string\",\"booleanFormVariable\":true}";
        expect(identitySvc.userGroups()).andReturn(asList(TEST_GROUP_ID));
        replay(identitySvc);
        Map<String, Object> startFormData = new HashMap<String, Object>() {{
            put("stringFormVariableWithDefaultValue", "default-string-value");
            put("stringFormVariable", null);
            put("booleanFormVariable", null);
        }};
        Map<String, Object> submittedFormValues = new HashMap<>();
        submittedFormValues.put("stringFormVariable", "non-default-string");
        submittedFormValues.put("booleanFormVariable", Boolean.TRUE);
        submittedFormValues.put("variableNotPresentInForm", "not-present-in-form");
        Capture<Map<String, Object>> processVariablesCapture = Capture.newInstance();

        variablesMapper.updateVariables(capture(processVariablesCapture), eq(validatedVariablesJson));
        expectLastCall().andStubAnswer(() -> {
            processVariablesCapture.getValue().put("stringFormVariableWithDefaultValue", "default-string-value");
            processVariablesCapture.getValue().put("stringFormVariable", "non-default-string");
            processVariablesCapture.getValue().put("booleanFormVariable", Boolean.TRUE);
            return null;
        });
        expect(taskSvc.getNextAssignedTask(anyObject(String.class))).andReturn(null);
        expect(formSvc.dryValidationAndCleanupStartForm(anyString(), eq(submittedFormValues), eq(PUBLIC_RESOURCES_DIRECTORY)))
                .andReturn(validatedVariablesJson);
        expect(variablesMapper.convertVariablesToEntities(capture(processVariablesCapture), anyObject(Map.class)))
                .andStubAnswer(processVariablesCapture::getValue);
        replay(formSvc, variablesMapper, taskSvc);

        processDefinitionSvc.start(
                "testProcessWithStartForm",
                submittedFormValues);

        verify(formSvc, variablesMapper, taskSvc, identitySvc);
        long instanceCount = getRuntimeService()
                .createProcessInstanceQuery()
                .count();
        assertTrue(instanceCount > 0);
        Execution execution = getRuntimeService().createExecutionQuery()
                .singleResult();
        String actualProcessStringVariable = (String) getRuntimeService().getVariable(execution.getId(), "stringFormVariable");
        Boolean actualProcessBooleanVariable = (Boolean) getRuntimeService().getVariable(execution.getId(), "booleanFormVariable");
        Object actualProcessVariableNotPresentInForm = getRuntimeService().getVariable(execution.getId(), "variableNotPresentInForm");
        Object actualProcessStringVariableWithDefaultValue = getRuntimeService().getVariable(execution.getId(), "stringFormVariableWithDefaultValue");
        assertEquals("non-default-string", actualProcessStringVariable);
        assertEquals("default-string-value", actualProcessStringVariableWithDefaultValue);
        assertEquals(Boolean.TRUE, actualProcessBooleanVariable);
        assertNull(actualProcessVariableNotPresentInForm);
    }

    @Test
    public void testStart_withStartForm_MultipleFileValue() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-with-start-form.bpmn");
        String validatedVariablesJson = "validatedJsonWithFileValues";
        List<Map<String, Object>> expectedFileValues = asList(
                getFileAsAttributesMap(getFile("testFile.png")),
                getFileAsAttributesMap(getFile("testFile.png")));
        Map<String, Object> submittedFormValues = new HashMap<String, Object>() {{
            put("testFiles", expectedFileValues);
        }};
        Capture<Map<String, Object>> processVariablesCapture = Capture.newInstance();

        variablesMapper.updateVariables(capture(processVariablesCapture), eq(validatedVariablesJson));
        expectLastCall().andStubAnswer(() -> {
            processVariablesCapture.getValue().put("testFiles", expectedFileValues);
            return null;
        });
        expect(formSvc.dryValidationAndCleanupStartForm(anyString(), eq(submittedFormValues), eq(PUBLIC_RESOURCES_DIRECTORY)))
                .andReturn(validatedVariablesJson);
        expect(taskSvc.getNextAssignedTask(anyObject(String.class))).andReturn(null);
        expect(identitySvc.userGroups()).andReturn(asList(TEST_GROUP_ID));
        expect(variablesMapper.convertVariablesToEntities(capture(processVariablesCapture), anyObject(Map.class)))
            .andStubAnswer(processVariablesCapture::getValue);
        replay(formSvc, identitySvc, variablesMapper, taskSvc);

        processDefinitionSvc.start("testProcessWithStartForm", submittedFormValues);

        verify(formSvc, identitySvc, variablesMapper, identitySvc);
        long instanceCount = getRuntimeService()
                .createProcessInstanceQuery()
                .count();
        assertTrue(instanceCount > 0);
        Execution execution = getRuntimeService().createExecutionQuery()
                .singleResult();
        List<Map<String, Object>> actualFileValues = (List<Map<String, Object>>) getRuntimeService().getVariable(execution.getId(), "testFiles");
        ListAssert.assertEquals(expectedFileValues, actualFileValues);
    }

    @Test
    public void testLoadRenderedStartForm() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-with-start-form.bpmn");
        Map<String, Object> expectedInitialFormVariables = new HashMap<>();
        expectedInitialFormVariables.put("stringFormVariable", null);
        expectedInitialFormVariables.put("stringFormVariableWithDefaultValue", "default-string-value");
        expectedInitialFormVariables.put("booleanFormVariable", null);
        String formWithData = "{ \"data\": {" +
                "\"stringFormVariable\":null," +
                "\"stringFormVariableWithDefaultValue\":\"default-string-value\"," +
                "\"booleanFormVariable\":null}}";
        expect(formSvc.getStartFormWithData(EasyMock.anyString(), eq(expectedInitialFormVariables), eq(PUBLIC_RESOURCES_DIRECTORY)))
                .andReturn(formWithData);
        expect(identitySvc.userGroups()).andReturn(asList(TEST_GROUP_ID));
        replay(formSvc, identitySvc);

        String actualFormWithData = processDefinitionSvc.loadRenderedStartForm("testProcessWithStartForm");

        assertEquals(actualFormWithData, formWithData);
        verify(formSvc);
    }

    @Test(expected = NotAuthorizedException.class)
    public void testLoadRenderedStartForm_notAuthorized() throws IOException, URISyntaxException {
        createDeployment("test-deployment",
                "test-process-startable-by-testUser.bpmn");
        expect(identitySvc.userId()).andReturn(ANOTHER_USER_ID);
        replay(identitySvc);

        processDefinitionSvc.loadRenderedStartForm("testProcessStartableByTestUser");
    }

    @Test
    public void testUserIsInCandidateGroup() {
        List<String> userGroups = asList("candidateGroup", "anotherGroup");
        expect(identitySvc.userGroups()).andReturn(userGroups);
        replay(identitySvc);
        List<IdentityLink> identityLinks = asList(
                makeIdentityLink(TEST_USER_ID, "candidateGroup", IdentityLinkType.CANDIDATE));

        boolean userIsInCandidateGroup = processDefinitionSvc.userIsInCandidateGroup(identityLinks);

        assertTrue(userIsInCandidateGroup);
        verify(identitySvc);
    }

    @Test
    public void testUserIsInCandidateGroup_notInCandidateGroup() {
        List<String> userGroups = asList("candidateGroup");
        expect(identitySvc.userGroups()).andReturn(userGroups);
        replay(identitySvc);
        List<IdentityLink> identityLinks = asList(
                makeIdentityLink(TEST_USER_ID, "anotherGroup", IdentityLinkType.CANDIDATE),
                makeIdentityLink(TEST_USER_ID, "candidateGroup", IdentityLinkType.ASSIGNEE),
                makeIdentityLink(TEST_USER_ID, "candidateGroup", IdentityLinkType.OWNER)
        );

        boolean userIsInCandidateGroup = processDefinitionSvc.userIsInCandidateGroup(identityLinks);

        assertFalse(userIsInCandidateGroup);
        verify(identitySvc);
    }

    @Test
    public void testUserIsInCandidateGroup_noCandidateGroups() {
        List<IdentityLink> identityLinks = Collections.emptyList();

        boolean userIsInCandidateGroup = processDefinitionSvc.userIsInCandidateGroup(identityLinks);

        assertFalse(userIsInCandidateGroup);
    }

    @Test
    public void testUserIsCandidate() {
        expect(identitySvc.userId()).andReturn(TEST_USER_ID);
        replay(identitySvc);
        IdentityLink testUserCandidateIdentityLink = makeIdentityLink(TEST_USER_ID, null, IdentityLinkType.CANDIDATE);
        IdentityLink anotherUserCandidateIdentityLink = makeIdentityLink(ANOTHER_USER_ID, null, IdentityLinkType.CANDIDATE);

        boolean userIsCandidate = processDefinitionSvc.userIsCandidate(
                asList(testUserCandidateIdentityLink, anotherUserCandidateIdentityLink));

        assertTrue(userIsCandidate);
        verify(identitySvc);
    }

    @Test
    public void testUserIsCandidate_userIsNotCandidate() {
        expect(identitySvc.userId()).andReturn(TEST_USER_ID);
        replay(identitySvc);
        IdentityLink anotherUserCandidateIdentityLink = makeIdentityLink(ANOTHER_USER_ID, null, IdentityLinkType.CANDIDATE);
        IdentityLink nullUserIdIdentityLink = makeIdentityLink(null, null, IdentityLinkType.CANDIDATE);
        IdentityLink testUserAssigneeIdentityLink = makeIdentityLink(null, null, IdentityLinkType.ASSIGNEE);

        assertFalse(processDefinitionSvc.userIsCandidate(asList(
                nullUserIdIdentityLink,
                anotherUserCandidateIdentityLink,
                testUserAssigneeIdentityLink)));
        verify(identitySvc);
    }

    @Test
    public void testUserIsCandidate_noCandidateLinks() {
        assertFalse(processDefinitionSvc.userIsCandidate(Collections.emptyList()));
    }

    private IdentityLink makeIdentityLink(String userId, String groupId, String type) {
        return new IdentityLink() {
            @Override
            public String getId() {
                return UUID.randomUUID().toString();
            }

            @Override
            public String getType() {
                return type;
            }

            @Override
            public String getUserId() {
                return userId;
            }

            @Override
            public String getGroupId() {
                return groupId;
            }

            @Override
            public String getTaskId() {
                return null;
            }

            @Override
            public String getProcessDefId() {
                return null;
            }

            @Override
            public String getTenantId() {
                return null;
            }
        };
    }
    
    @Test
    @org.camunda.bpm.engine.test.Deployment(resources = "test-process-with-start-form.bpmn")
    public void testLoadStartFormDto() throws IOException {
        expect(identitySvc.userGroups()).andReturn(Arrays.asList("testGroup"));
        replay(identitySvc);

        FormDto actual = processDefinitionSvc.loadStartForm("testProcessWithStartForm");
        
        assertEquals("testStartForm", actual.getKey());
    }

    @Test
    @org.camunda.bpm.engine.test.Deployment(resources = "simple-test-process.bpmn")
    public void testLoadStartFormDto_WithoutStartForm() throws IOException {
        expect(identitySvc.userGroups()).andReturn(Arrays.asList("responsibles"));
        replay(identitySvc);

        FormDto actual = processDefinitionSvc.loadStartForm("myProcess");
        
        assertNull(actual.getKey());
    }

}
