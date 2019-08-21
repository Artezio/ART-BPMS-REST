package com.artezio.camunda.el;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.bpm.engine.impl.javax.el.BeanELResolver;
import org.camunda.bpm.engine.impl.javax.el.ELContext;
import org.camunda.bpm.engine.impl.javax.el.ELException;
import org.camunda.bpm.engine.impl.javax.el.MethodNotFoundException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class DeserealizingBeanELResolver extends BeanELResolver {
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public Object getValue(ELContext context, Object base, Object property) {
        if (base instanceof Map) {
            Object result = ((Map) base).get(property);
            context.setPropertyResolved(true);
            return result;
        } else if (base instanceof List) {
            int index = ((Long) property).intValue();
            Object result = ((List) base).get(index);
            context.setPropertyResolved(true);
            return result;
        } else {
            return super.getValue(context, base, property);
        }
    }

    @Override
    public Object invoke(ELContext context, Object base, Object method, Class<?>[] paramTypes, Object[] params) {
        if (context == null) {
            throw new NullPointerException();
        }
        if (!(base instanceof List)) {
            Method target = findMethod(base, method.toString(), params);
            if (target == null) {
                throw new MethodNotFoundException("Cannot find method " + method + " with " + params.length + " parameters in " + base.getClass());
            }
            params = deserializeArgumentsIfRequired(target, params);
            return super.invoke(context, base, target.getName(), paramTypes, params);
        }
        return super.invoke(context, base, method, paramTypes, params);
    }

    protected Object[] deserializeArgumentsIfRequired(Method target, Object[] params) {
        Class<?>[] targetParameterTypes = target.getParameterTypes();
        for (int paramIndex = 0; paramIndex < params.length; paramIndex++) {
            try {
                Class<?> targetParameterType = targetParameterTypes[paramIndex];
                Object argument = params[paramIndex];
                if (argument instanceof Map) {
                    params[paramIndex] = OBJECT_MAPPER.readValue(OBJECT_MAPPER.writeValueAsString(argument), targetParameterType);
                }
            } catch (IOException e) {
                throw new ELException("Could not deserialize argument " + paramIndex, e);
            }
        }
        return params;
    }

    protected Method findMethod(Object base, String name, Object[] params) {
        for (Method method : base.getClass().getMethods()) {
            if ((method.getName().equals(name)) && (method.getParameterCount() == params.length)) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                boolean allParamTypesMatch = true;
                for (int i = 0; i < params.length; i++) {
                    if (params[i] != null) {
                        if (!(params[i] instanceof Map) && !(parameterTypes[i].isInstance(params[i]))) {
                            allParamTypesMatch = false;
                        }
                    }
                }
                if (allParamTypesMatch) {
                    return method;
                }
            }
        }
        return null;
    }

}
