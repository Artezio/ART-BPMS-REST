package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.VariableValueRepresentation;
import com.artezio.bpm.rest.dto.message.CorrelationMessageRepresentation;
import com.artezio.bpm.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.MessageCorrelationBuilderImpl;
import org.camunda.bpm.engine.runtime.MessageCorrelationBuilder;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

public class MessageSvcTest extends ServiceTest {

    private MessageSvc messageSvc = new MessageSvc();

    @Before
    public void init() throws NoSuchFieldException {
        Field runtimeServiceField = messageSvc.getClass().getDeclaredField("runtimeService");
        setField(messageSvc, runtimeServiceField, getRuntimeService());
    }

    @Test
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_CorrelateOneMessage() {
        String messageName = "Test message";
        boolean isAll = false;
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, null,
                isAll, false, true, messageSvcTest.getProcessInstanceId());

        Response response = messageSvc.deliverMessage(messageRepresentation);

        assertEquals(OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_CorrelateAllMessages() {
        String messageName = "Test message";
        boolean isAll = true;
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, null,
                isAll, false, true, messageSvcTest.getProcessInstanceId());

        Response response = messageSvc.deliverMessage(messageRepresentation);

        assertEquals(OK.getStatusCode(), response.getStatus());
        assertNotNull(response.getEntity());
    }

    @Test
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_ResultIsDisabled() {
        String messageName = "Test message";
        boolean isAll = false;
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, null,
                isAll, false, false, messageSvcTest.getProcessInstanceId());

        Response response = messageSvc.deliverMessage(messageRepresentation);

        assertEquals(NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test(expected = InvalidRequestException.class)
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_NoMessageName() {
        String messageName = null;
        boolean isAll = false;
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, null,
                isAll, false, true, messageSvcTest.getProcessInstanceId());

        messageSvc.deliverMessage(messageRepresentation);
    }

    @Test(expected = InvalidRequestException.class)
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_TenantIdPresentsAndWithoutTenantIdIsTrue() {
        String messageName = "Test message";
        String tenantId = "tenantId";
        boolean isWithoutTenantId = true;
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, tenantId,
                false, true, isWithoutTenantId, messageSvcTest.getProcessInstanceId());

        messageSvc.deliverMessage(messageRepresentation);
    }

    @Test(expected = BadUserRequestException.class)
    @Deployment(resources = "message-svc-test.bpmn")
    public void testDeliverMessage_TenantIdAndProcessInstanceIdAreSet() {
        String messageName = "Test message";
        ProcessInstance messageSvcTest = getRuntimeService().startProcessInstanceByKey("messageSvcTest");
        String tenantId = "tenantId";
        CorrelationMessageRepresentation messageRepresentation = buildCorrelationMessageRepresentation(messageName, tenantId,
                false, false, true, messageSvcTest.getProcessInstanceId());

        messageSvc.deliverMessage(messageRepresentation);
    }

    @Test
    public void testCreateMessageCorrelationBuilder() {
        CorrelationMessageRepresentation correlationMessageRepresentation = buildCorrelationMessageRepresentation(
                "messageName", "tenantId", true, true, true, emptyMap(),
                emptyMap(), "businessKey", "processInstanceId", emptyMap(), emptyMap());

        MessageCorrelationBuilder messageCorrelationBuilder = messageSvc.createMessageCorrelationBuilder(correlationMessageRepresentation);

        assertPayloadsAreEquivalent(correlationMessageRepresentation, messageCorrelationBuilder);
    }

    private void assertPayloadsAreEquivalent(CorrelationMessageRepresentation correlationMessageRepresentation,
                                             MessageCorrelationBuilder messageCorrelationBuilder) {
        MessageCorrelationBuilderImpl builder = (MessageCorrelationBuilderImpl) messageCorrelationBuilder;
        assertEquals(correlationMessageRepresentation.getMessageName(), builder.getMessageName());
        assertEquals(correlationMessageRepresentation.getTenantId(), builder.getTenantId());
        assertEquals(correlationMessageRepresentation.isWithoutTenantId(), builder.isTenantIdSet());
        assertEquals(correlationMessageRepresentation.getProcessVariables(), builder.getPayloadProcessInstanceVariables());
        assertEquals(correlationMessageRepresentation.getProcessVariablesLocal(), builder.getPayloadProcessInstanceVariablesLocal());
        assertEquals(correlationMessageRepresentation.getBusinessKey(), builder.getBusinessKey());
        assertEquals(correlationMessageRepresentation.getProcessInstanceId(), builder.getProcessInstanceId());
        Map<String, VariableValueRepresentation> correlationKeys = correlationMessageRepresentation.getCorrelationKeys();
        if (correlationKeys != null && !correlationKeys.isEmpty()) {
            assertEquals(correlationKeys, builder.getCorrelationProcessInstanceVariables());
        }
        Map<String, VariableValueRepresentation> localCorrelationKeys = correlationMessageRepresentation.getLocalCorrelationKeys();
        if (localCorrelationKeys != null && !localCorrelationKeys.isEmpty()) {
            assertEquals(localCorrelationKeys, builder.getCorrelationLocalVariables());
        }
    }

    private CorrelationMessageRepresentation buildCorrelationMessageRepresentation(String messageName, String tenantId, boolean isAll,
                                                                                   boolean isWithoutTenantId, boolean isResultEnabled,
                                                                                   String processInstanceId) {
        return buildCorrelationMessageRepresentation(messageName, tenantId, isAll, isWithoutTenantId, isResultEnabled,
                emptyMap(), emptyMap(), null, processInstanceId, emptyMap(), emptyMap());
    }


    private CorrelationMessageRepresentation buildCorrelationMessageRepresentation(String messageName, String tenantId, boolean isAll,
                                                                                   boolean isWithoutTenantId, boolean isResultEnabled,
                                                                                   Map<String, VariableValueRepresentation> processVariables,
                                                                                   Map<String, VariableValueRepresentation> processVariablesLocal,
                                                                                   String businessKey, String processInstanceId,
                                                                                   Map<String, VariableValueRepresentation> correlationKeys,
                                                                                   Map<String, VariableValueRepresentation> localCorrelationKeys) {
        CorrelationMessageRepresentation correlationMessageRepresentation = new CorrelationMessageRepresentation();
        correlationMessageRepresentation.setMessageName(messageName);
        correlationMessageRepresentation.setTenantId(tenantId);
        correlationMessageRepresentation.setAll(isAll);
        correlationMessageRepresentation.setResultEnabled(isResultEnabled);
        correlationMessageRepresentation.setWithoutTenantId(isWithoutTenantId);
        correlationMessageRepresentation.setCorrelationKeys(correlationKeys);
        correlationMessageRepresentation.setLocalCorrelationKeys(localCorrelationKeys);
        correlationMessageRepresentation.setProcessVariables(processVariables);
        correlationMessageRepresentation.setProcessVariablesLocal(processVariablesLocal);
        correlationMessageRepresentation.setBusinessKey(businessKey);
        correlationMessageRepresentation.setProcessInstanceId(processInstanceId);
        return correlationMessageRepresentation;
    }

}
