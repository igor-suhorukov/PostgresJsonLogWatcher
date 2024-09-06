package com.github.isuhorukov.log.watcher.otel;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.qameta.allure.Description;
import io.qameta.allure.Step;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.opentest4j.TestAbortedException;

import java.util.AbstractMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Aspect for {@link org.junit.jupiter.api.Test} and {@link io.qameta.allure.Step}
 * to report test results with OpenTelemetry.
 */
@Aspect
public class OtelTestAspect {
    private final Tracer tracer = GlobalOpenTelemetry.get().getTracer("maven-failsafe-plugin");

    @Pointcut("@annotation(org.junit.jupiter.api.Test)")
    public void withTestAnnotation() {
        //pointcut body, should be empty
    }

    @Pointcut("@annotation(io.qameta.allure.Step)")
    public void withStepAnnotation() {
        //pointcut body, should be empty
    }

    @Pointcut("execution(* *(..))")
    public void anyMethod() {
        //pointcut body, should be empty
    }


    /**
     * AOP advice for any methods in the project excluding private methods in PostgreSqlJson and
     * LogEnricherPostgreSql classes, and methods named getFsWatchService and enricherApplicationName.
     * This advice proceeds with a span created by the proceedWithSpan method, setting the span name
     * to the signature of the intercepted method. It also sets the span attributes "arg: {parameterName}"
     * to the value of the corresponding parameter, and "result" to the value returned by the intercepted
     * method if it is not null.
     *
     * @param pjp the ProceedingJoinPoint representing the intercepted method
     * @return the result of the intercepted method
     * @throws Throwable if an error occurs during execution
     */
    @Around("(execution(!private * com.github.isuhorukov.log.watcher.PostgreSqlJson.*(..)) " +
            "|| execution(!private * com.github.isuhorukov.log.watcher.LogEnricherPostgreSql.*(..))) " +
            "&& !execution(* *getFsWatchService(..)) && !execution(* *enricherApplicationName(..))")
    public Object aroundAnyMethodsFromProject(ProceedingJoinPoint pjp) throws Throwable {
        return proceedWithSpan(pjp, pjp.getSignature().toString(), result -> {
            Span span = result.getKey();
            final String[] parameterNames = ((MethodSignature) pjp.getSignature()).getParameterNames();
            Object[] args = pjp.getArgs();
            for (int i = 0; i < Math.max(parameterNames.length, args.length); i++) {
                span.setAttribute("arg: "+parameterNames[i], String.valueOf(args[i]));
            }
            if(result.getValue()!=null){
                span.setAttribute("result", String.valueOf(result.getValue()));
            }
        }, (e) -> {});
    }
    /**
     * Around advice for methods annotated with @Test from the anyMethod() pointcut.
     * This method creates a span using the OpenTelemetry API and proceeds with the join point.
     * If the method execution is successful, it sets the attribute "junit.test.result" to "SUCCESSFUL"
     * and the attribute "result" to the result value. If the method execution fails, it sets the
     * attribute "junit.test.result" to either "ABORTED" or "FAILED" depending on the exception type,
     * and the attribute "junit.test.result.reason" to the exception message.
     *
     * @param  pjp the ProceedingJoinPoint representing the method invocation
     * @return the result of the method invocation
     * @throws Throwable if an error occurs during method invocation
     */
    @Around("anyMethod() && withTestAnnotation()")
    public Object aroundTestAnnotation(ProceedingJoinPoint pjp) throws Throwable {
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        final Description annotation = methodSignature.getMethod().getAnnotation(Description.class);
        return proceedWithSpan(pjp, getSpanName(pjp, getDescriptionText(annotation)),
                (result)-> {
                    Span span = result.getKey();
                    span.setAttribute("junit.test.result", "SUCCESSFUL");
                    if(result.getValue()!=null){
                        span.setAttribute("result", String.valueOf(result.getValue()));
                    }
                },
                (e)->{
                    Span span = e.getKey();
                    if(e.getValue() instanceof TestAbortedException) {
                        span.setAttribute("junit.test.result", "ABORTED");
                    } else {
                        span.setAttribute("junit.test.result", "FAILED");
                    }
                    span.setAttribute("junit.test.result.reason", e.getValue().getMessage());
                });
    }

    /**
     * Around advice for methods annotated with @Step from the anyMethod() pointcut.
     * This method creates a span using the OpenTelemetry API and proceeds with the join point.
     *
     * @param  pjp the ProceedingJoinPoint representing the method invocation
     * @return the result of the method invocation
     * @throws Throwable if an error occurs during method invocation
     */
    @Around("anyMethod() && withStepAnnotation()")
    public Object aroundStepAnnotation(ProceedingJoinPoint pjp) throws Throwable {
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        final Step annotation = methodSignature.getMethod().getAnnotation(Step.class);
        return proceedWithSpan(pjp, getSpanName(pjp, getDescriptionText(annotation)), (s)->{}, (e)->{});
    }

    /**
     * Executes the given join point with a span created using the OpenTelemetry API.
     *
     * @param  pjp            the ProceedingJoinPoint representing the method invocation
     * @param  spanName       the name of the span
     * @param  successHandler the consumer to handle the success result
     * @param  errorHandler   the consumer to handle the error result
     * @return                the result of the method invocation
     * @throws Throwable      if an error occurs during method invocation
     */
    private Object proceedWithSpan(ProceedingJoinPoint pjp, String spanName,
                                   Consumer<Map.Entry<Span, Object>> successHandler,
                                   Consumer<Map.Entry<Span, Exception>> errorHandler) throws Throwable {
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName);
        Span span = spanBuilder.setSpanKind(SpanKind.INTERNAL).startSpan();
        try(Scope scope = span.makeCurrent())  {
            Object proceed = pjp.proceed();
            successHandler.accept(new AbstractMap.SimpleImmutableEntry<>(span, proceed));
            return proceed;
        } catch (Exception ex) {
            errorHandler.accept(new AbstractMap.SimpleImmutableEntry<>(span, ex));
            span.recordException(ex);
            throw ex;
        }
        finally {
            span.end();
        }
    }

    private static String getSpanName(ProceedingJoinPoint pjp, String descriptionText) {
        return descriptionText != null ? descriptionText : pjp.getSignature().getName();
    }

    private static String getDescriptionText(Description description) {
        return description != null ? description.value() : null;
    }

    private static String getDescriptionText(Step step) {
        return step != null ? step.value() : null;
    }
}
