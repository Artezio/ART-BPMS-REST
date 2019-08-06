package com.artezio.bpm.services;

import com.artezio.bpm.rest.dto.SignalRepresentation;
import com.artezio.bpm.rest.dto.VariableValueRepresentation;
import com.artezio.bpm.rest.exception.InvalidRequestException;
import org.camunda.bpm.engine.BadUserRequestException;
import org.camunda.bpm.engine.impl.SignalEventReceivedBuilderImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.runtime.SignalEventReceivedBuilder;
import org.camunda.bpm.engine.test.Deployment;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.mockito.internal.util.reflection.FieldSetter.setField;

public class SignalSvcTest extends ServiceTest {

    private SignalSvc signalSvc = new SignalSvc();

    @Before
    public void init() throws NoSuchFieldException {
        Field runtimeServiceField = signalSvc.getClass().getDeclaredField("runtimeService");
        setField(signalSvc, runtimeServiceField, getRuntimeService());
    }

    @Test
    public void testThrowSignal_TenantIdIsSpecified() {
        SignalRepresentation signalRepresentation = buildSignalRepresentation("signalName", "tenantId", null, false, emptyMap());

        signalSvc.throwSignal(signalRepresentation);
    }

    @Test
    @Deployment(resources = "signal-svc-test.bpmn")
    public void testThrowSignal_ExecutionIdIsSpecified() {
        getRuntimeService().startProcessInstanceByKey("signalSvcTest");
        String signalReceiverExecutionId = getRuntimeService().createExecutionQuery().active().list().stream()
                .filter(execution -> ((ExecutionEntity) execution).getActivityId() != null)
                .filter(execution -> ((ExecutionEntity) execution).getActivityId().equals("signalReceiver"))
                .findFirst()
                .get()
                .getId();

        String signalName = "Test signal";
        SignalRepresentation signalRepresentation = buildSignalRepresentation(signalName, null, signalReceiverExecutionId, false, emptyMap());

        signalSvc.throwSignal(signalRepresentation);
    }

    @Test(expected = InvalidRequestException.class)
    public void testThrowSignal_SignalNameIsNotGiven() {
        SignalRepresentation signalRepresentation = buildSignalRepresentation(null, null, "executionId", false, emptyMap());

        signalSvc.throwSignal(signalRepresentation);
    }

    @Test(expected = BadUserRequestException.class)
    public void testThrowSignal_TenantIdAndExecutionIdAreSpecified() {
        SignalRepresentation signalRepresentation = buildSignalRepresentation("signalName", "tenantId", "executionId", false, emptyMap());

        signalSvc.throwSignal(signalRepresentation);
    }

    @Test
    public void testCreateSignalEventReceivedBuilder() {
        SignalRepresentation signalRepresentation = buildSignalRepresentation("signalName", "tenantId", "executionId", false, emptyMap());

        SignalEventReceivedBuilder signalEventReceivedBuilder = signalSvc.createSignalEventReceivedBuilder(signalRepresentation);

        assertPayloadsEquivalent(signalRepresentation, signalEventReceivedBuilder);
    }

    private SignalRepresentation buildSignalRepresentation(String name, String tenantId, String executionId, boolean withoutTenantId, Map<String, VariableValueRepresentation> variables) {
        SignalRepresentation signalRepresentation = new SignalRepresentation();
        signalRepresentation.setName(name);
        signalRepresentation.setExecutionId(executionId);
        signalRepresentation.setTenantId(tenantId);
        signalRepresentation.setWithoutTenantId(withoutTenantId);
        signalRepresentation.setVariables(variables);
        return signalRepresentation;
    }

    private void assertPayloadsEquivalent(SignalRepresentation signalRepresentation, SignalEventReceivedBuilder signalEventReceivedBuilder) {
        SignalEventReceivedBuilderImpl signalEventReceivedBuilderImpl = (SignalEventReceivedBuilderImpl) signalEventReceivedBuilder;
        assertEquals(signalRepresentation.getName(), signalEventReceivedBuilderImpl.getSignalName());
        assertEquals(signalRepresentation.getExecutionId(), signalEventReceivedBuilderImpl.getExecutionId());
        assertEquals(signalRepresentation.getTenantId(), signalEventReceivedBuilderImpl.getTenantId());
        assertEquals(signalRepresentation.getVariables(), signalEventReceivedBuilderImpl.getVariables());
    }

}
