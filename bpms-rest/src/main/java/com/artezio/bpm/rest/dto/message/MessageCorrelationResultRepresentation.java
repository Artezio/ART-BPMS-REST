package com.artezio.bpm.rest.dto.message;

import com.artezio.bpm.rest.dto.runtime.ExecutionRepresentation;
import com.artezio.bpm.rest.dto.runtime.ProcessInstanceRepresentation;
import org.camunda.bpm.engine.runtime.MessageCorrelationResult;
import org.camunda.bpm.engine.runtime.MessageCorrelationResultType;

public class MessageCorrelationResultRepresentation {

    private MessageCorrelationResultType resultType;
    private ExecutionRepresentation execution;
    private ProcessInstanceRepresentation processInstance;

    public static MessageCorrelationResultRepresentation fromMessageCorrelationResult(MessageCorrelationResult result) {
        MessageCorrelationResultRepresentation representation = new MessageCorrelationResultRepresentation();
        if (result != null) {
            representation.resultType = result.getResultType();
            if (result.getProcessInstance() != null) {
                representation.processInstance = ProcessInstanceRepresentation.fromProcessInstance(result.getProcessInstance());
            } else if (result.getExecution() != null) {
                representation.execution = ExecutionRepresentation.fromExecution(result.getExecution());
            }
        }
        return representation;
    }

    public MessageCorrelationResultType getResultType() {
        return resultType;
    }

    public ExecutionRepresentation getExecution() {
        return execution;
    }

    public ProcessInstanceRepresentation getProcessInstance() {
        return processInstance;
    }

}
