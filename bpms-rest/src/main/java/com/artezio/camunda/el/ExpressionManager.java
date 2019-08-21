package com.artezio.camunda.el;

import org.camunda.bpm.engine.impl.javax.el.CompositeELResolver;
import org.camunda.bpm.engine.impl.javax.el.ELResolver;

public class ExpressionManager extends org.camunda.bpm.engine.impl.el.ExpressionManager {
    @Override
    protected ELResolver createElResolver() {
        ELResolver baseResolver = super.createElResolver();
        CompositeELResolver result = new CompositeELResolver();
        result.add(new DeserealizingBeanELResolver());
        result.add(baseResolver);
        return result;
    }
}
