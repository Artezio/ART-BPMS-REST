package org.camunda.config;

public class JtaProcessConfiguration extends org.camunda.bpm.engine.impl.cfg.JtaProcessEngineConfiguration {
    public JtaProcessConfiguration() {
        super();
        expressionManager = new org.camunda.el.ExpressionManager();
    }
}
