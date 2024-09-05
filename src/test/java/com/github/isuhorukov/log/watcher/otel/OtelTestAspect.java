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


    @Around("(execution(!private * com.github.isuhorukov.log.watcher.PostgreSqlJson.*(..)) " +
            "|| execution(!private * com.github.isuhorukov.log.watcher.LogEnricherPostgreSql.*(..))) " +
            "&& !execution(* *getFsWatchService(..)) && !execution(* *enricherApplicationName(..))")
    public Object aroundAnyMethodsFromProject(ProceedingJoinPoint pjp) throws Throwable {
        return proceedWithSpan(pjp, pjp.getSignature().toString(), result -> {
            Span span = result.getKey();
            Object[] args = pjp.getArgs();
            for (int idx = 0; idx < args.length; idx++) {
                span.setAttribute("arg[" + idx+"]", String.valueOf(args[idx]));
            }
            if(result.getValue()!=null){
                span.setAttribute("result", String.valueOf(result.getValue()));
            }
        }, (e) -> {});
    }
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

    @Around("anyMethod() && withStepAnnotation()")
    public Object aroundStepAnnotation(ProceedingJoinPoint pjp) throws Throwable {
        final MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        final Step annotation = methodSignature.getMethod().getAnnotation(Step.class);
        return proceedWithSpan(pjp, getSpanName(pjp, getDescriptionText(annotation)), (s)->{}, (e)->{});
    }

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
