package com.chatbot.logging;

import static com.chatbot.logging.LoggingBean.ApiType.CONTROLLER;
import static com.chatbot.logging.LoggingBean.ApiType.EXTERNAL;
import static com.chatbot.logging.LoggingBean.ApiType.REPOSITORY;
import static com.chatbot.logging.LoggingBean.ApiType.SCHEDULER;
import static com.chatbot.logging.LoggingBean.ApiType.SERVICE;

import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.filter.CorrelationIdFilter;
import java.util.Arrays;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

  public static final String LAYER = "layer";

  private static final String[] SENSITIVE_KEYWORDS = {
    "token", "secret", "password", "passwd", "pwd", "credential", "apikey", "jwt",
    "authorization", "clientid"
  };

  @Pointcut("execution(* com.chatbot.controller..*(..))")
  public void controllerPointCut() {}

  @Pointcut("execution(* com.chatbot.repository..*(..))")
  public void repositoryPointCut() {}

  @Pointcut("execution(* com.chatbot.service..*(..))")
  public void servicePointCut() {}

  @Pointcut("execution(* com.chatbot.client..*(..))")
  public void externalServicePointCut() {}

  @Pointcut("execution(* com.chatbot.scheduler..*(..))")
  public void schedulerPointCut() {}

  @Around("controllerPointCut()")
  public Object logAroundController(ProceedingJoinPoint pjp) throws Throwable {
    return logAround(pjp, CONTROLLER);
  }

  @Around("servicePointCut()")
  public Object logAroundService(ProceedingJoinPoint pjp) throws Throwable {
    return logAround(pjp, SERVICE);
  }

  @Around("externalServicePointCut()")
  public Object logAroundExternalService(ProceedingJoinPoint pjp) throws Throwable {
    return logAround(pjp, EXTERNAL);
  }

  @Around("repositoryPointCut()")
  public Object logAroundRepository(ProceedingJoinPoint pjp) throws Throwable {
    return logAround(pjp, REPOSITORY);
  }

  @Around("schedulerPointCut()")
  public Object logAroundScheduler(ProceedingJoinPoint pjp) throws Throwable {
    return logAround(pjp, SCHEDULER);
  }

  @AfterThrowing(value = "controllerPointCut()", throwing = "ex")
  public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
    CodeSignature signature = (CodeSignature) joinPoint.getSignature();
    Logger logger = LoggerFactory.getLogger(signature.getDeclaringType());
    String method = signature.getName();
    String params = Arrays.toString(signature.getParameterNames());
    String message =
        ex.getMessage() != null
            ? ex.getMessage()
            : (ex.getCause() != null ? ex.getCause().getMessage() : ex.getClass().getSimpleName());

    String previousLayer = MDC.get(LAYER);
    MDC.put(LAYER, CONTROLLER.name());
    try {
      if (ex instanceof NonRetryableException) {
        logger.warn("event=rejected method={} params={} msg=\"{}\"", method, params, message);
      } else {
        logger.error("event=exception method={} params={} msg=\"{}\"", method, params, message, ex);
      }
    } finally {
      restoreLayer(previousLayer);
    }
  }

  private Object logAround(ProceedingJoinPoint pjp, LoggingBean.ApiType apiType) throws Throwable {
    CodeSignature signature = (CodeSignature) pjp.getSignature();
    Logger logger = LoggerFactory.getLogger(resolveLoggerClass(pjp, signature, apiType));

    boolean ownsCorrelationId = MDC.get(CorrelationIdFilter.CORRELATION_ID) == null;
    if (ownsCorrelationId) {
      MDC.put(CorrelationIdFilter.CORRELATION_ID, UUID.randomUUID().toString().substring(0, 8));
    }
    String previousLayer = MDC.get(LAYER);
    MDC.put(LAYER, apiType.name());
    long start = System.currentTimeMillis();
    try {
      Object result = pjp.proceed();
      long durationMs = System.currentTimeMillis() - start;
      if (logger.isInfoEnabled()) {
        LoggingBean bean =
            LoggingBean.builder()
                .method(signature.getName())
                .parameters(signature.getParameterNames())
                .arguments(maskSensitive(pjp.getArgs(), signature.getParameterNames()))
                .durationMs(durationMs)
                .build();
        logger.info(bean.toString());
      }
      return result;
    } finally {
      restoreLayer(previousLayer);
      if (ownsCorrelationId) {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID);
      }
    }
  }

  private Object[] maskSensitive(Object[] args, String[] names) {
    if (args == null) {
      return null;
    }
    Object[] masked = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      String name = (names != null && i < names.length) ? names[i] : "";
      masked[i] = isSensitive(name) ? "***" : args[i];
    }
    return masked;
  }

  private boolean isSensitive(String parameterName) {
    String lower = parameterName.toLowerCase();
    for (String keyword : SENSITIVE_KEYWORDS) {
      if (lower.contains(keyword)) {
        return true;
      }
    }
    return false;
  }

  private void restoreLayer(String previousLayer) {
    if (previousLayer != null) {
      MDC.put(LAYER, previousLayer);
    } else {
      MDC.remove(LAYER);
    }
  }

  private Class<?> resolveLoggerClass(
      ProceedingJoinPoint pjp, CodeSignature signature, LoggingBean.ApiType apiType) {
    if (apiType == REPOSITORY && pjp.getTarget() != null) {
      for (Class<?> iface : pjp.getTarget().getClass().getInterfaces()) {
        if (iface.getPackageName().startsWith("com.chatbot.repository")) {
          return iface;
        }
      }
    }
    return signature.getDeclaringType();
  }
}
