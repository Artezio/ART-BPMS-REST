package com.artezio.logging;

import javax.inject.Inject;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.security.Principal;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.CONFIG;

@Log
@Interceptor
public class LogInterceptor {

    @Inject
    private Principal principal;

    @AroundInvoke
    public Object process(InvocationContext invocationContext) throws Exception {
        Method method = invocationContext.getMethod();
        Log annotation = method.getAnnotation(Log.class);
        Level level = Level.parse(annotation.level().name());
        Logger logger = getLogger(invocationContext);
        if (!logger.isLoggable(level)) {
            return invocationContext.proceed();
        }

        if (getPrincipalName() == null && annotation.principalsOnly()) {
            return invocationContext.proceed();
        }

        Object result;
        String methodName = method.getName();
        String logMessagePattern = getLogMessagePattern(methodName, level);

        String beforeExecuteMessage = annotation.beforeExecuteMessage();
        if (!beforeExecuteMessage.isEmpty()) {
            logMessage(invocationContext, level, logMessagePattern, beforeExecuteMessage);
        }

        try {
            result = invocationContext.proceed();
        } catch (Exception exception) {
            logger.log(Level.SEVERE, MessageFormat.format("Thrown exception {0}: {1}. Details in server stack trace.",
                    exception.getClass(), exception.getMessage()));
            throw exception;
        }

        if (annotation.logResult()) {
            logger.log(level,
                    MessageFormat.format("{0} - Result is {1}", methodName, String.valueOf(result)));
        }

        String afterExecuteMessage = annotation.afterExecuteMessage();
        if (!afterExecuteMessage.isEmpty()) {
            logMessage(invocationContext, level, logMessagePattern, afterExecuteMessage);
        }

        return result;
    }

    private void logMessage(InvocationContext invocationContext, Level level, String logMessagePattern, String message) {
        Object[] parameters = invocationContext.getParameters();
        String patternWithEscapedQuotes = escapeQuotes(message);
        message = MessageFormat.format(patternWithEscapedQuotes, parameters);
        getLogger(invocationContext).log(level, MessageFormat.format(logMessagePattern, message));
    }

    private String getLogMessagePattern(String methodName, Level level) {
        String principalInfoSuffix = "principal: " + getPrincipalName();
        return level == CONFIG
                    ? MessageFormat.format("{0} - {1} ({2})", methodName, "{0}", principalInfoSuffix)
                    : MessageFormat.format("{0} ({1})",  "{0}", principalInfoSuffix);
    }

    private String escapeQuotes(String string) {
        return string.replaceAll("'", "''");
    }

    private String getPrincipalName() {
        return principal != null ? principal.getName() : null;
    }

    protected Logger getLogger(InvocationContext invocationContext) {
        return Logger.getLogger(invocationContext.getTarget().getClass().getName());
    }

}
