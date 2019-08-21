package com.artezio.camunda.config;

public class JtaProcessConfiguration extends org.camunda.bpm.engine.impl.cfg.JtaProcessEngineConfiguration {
    public JtaProcessConfiguration() {
        super();
        expressionManager = new com.artezio.camunda.el.ExpressionManager();
    }
}
