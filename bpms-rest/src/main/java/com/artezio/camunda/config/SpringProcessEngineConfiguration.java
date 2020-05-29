package com.artezio.camunda.config;

public class SpringProcessEngineConfiguration extends org.camunda.bpm.engine.spring.SpringProcessEngineConfiguration {
    public SpringProcessEngineConfiguration() {
        super();
        expressionManager = new com.artezio.camunda.el.ExpressionManager();
    }
}
