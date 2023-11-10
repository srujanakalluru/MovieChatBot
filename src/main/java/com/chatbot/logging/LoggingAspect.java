package com.chatbot.logging;

import static com.chatbot.logging.LoggingBean.ApiType.*;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.CodeSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Getter
@Setter
@Slf4j(topic = "MovieRecommendationsApplication")
public class LoggingAspect {

  /** Pointcut for controller */
  @Pointcut("execution(* com.recommendations.controller..*(..))")
  public void controllerPointCut() {
    // Method is empty as this is just a Pointcut
  }

  /** Pointcut for repository */
  @Pointcut("execution(* com.recommendations.repository..*(..))")
  public void repositoryPointCut() {
    // Method is empty as this is just a Pointcut
  }

  /** Pointcut for service */
  @Pointcut("execution(* com.recommendations.service..*(..))")
  public void servicePointCut() {
    // Method is empty as this is just a Pointcut
  }

  /** Pointcut for external client calls */
  @Pointcut("execution(* com.recommendations.client..*(..))")
  public void externalServicePointCut() {
    // Method is empty as this is just a Pointcut
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @throws Throwable throwable
   */
  @Around("controllerPointCut()")
  public Object logAroundController(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    return logAroundBean(proceedingJoinPoint, CONTROLLER);
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @throws Throwable throwable
   */
  @Around("servicePointCut()")
  public Object logAroundService(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    return logAroundBean(proceedingJoinPoint, SERVICE);
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @throws Throwable throwable
   */
  @Around("externalServicePointCut()")
  public Object logAroundExternalService(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    return logAroundBean(proceedingJoinPoint, EXTERNAL);
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @throws Throwable throwable
   */
  @Around("repositoryPointCut()")
  public Object logAroundRepository(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
    return logAroundBean(proceedingJoinPoint, REPOSITORY);
  }

  /**
   * @param joinPoint joinPoint
   */
  @AfterThrowing(value = "controllerPointCut()", throwing = "ex")
  public void logAfterThrowingExceptionCall(JoinPoint joinPoint, Throwable ex) {
    logErrorBean(joinPoint, ex);
  }

  private void logErrorBean(JoinPoint joinPoint, Throwable ex) {
    CodeSignature signature = (CodeSignature) joinPoint.getSignature();
    LoggingBean bean =
        LoggingBean.builder()
            .apiType(ERROR)
            .className(joinPoint.getTarget().getClass().getSimpleName())
            .method(signature.getName())
            .parameters(signature.getParameterNames())
            .arguments(joinPoint.getArgs())
            .stackTrace(null != ex.getMessage() ? ex.getMessage() : ex.getCause().getMessage())
            .build();
    log.error(bean.toString());
  }

  /**
   * @param joinPoint joinPoint
   * @param apiType apiType
   * @param detailMessage detailMessage
   */
  private void logBeforeBean(
      JoinPoint joinPoint, LoggingBean.ApiType apiType, String detailMessage) {
    CodeSignature signature = (CodeSignature) joinPoint.getSignature();
    LoggingBean bean =
        LoggingBean.builder()
            .apiType(apiType)
            .className(joinPoint.getTarget().getClass().getSimpleName())
            .method(signature.getName())
            .parameters(signature.getParameterNames())
            .arguments(joinPoint.getArgs())
            .detailMessage(detailMessage)
            .build();
    log.info(bean.toString());
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @param apiType apiType
   * @return Object object
   * @throws Throwable throwable
   */
  private Object logAroundBean(ProceedingJoinPoint proceedingJoinPoint, LoggingBean.ApiType apiType)
      throws Throwable {
    return logAroundBean(proceedingJoinPoint, apiType, null);
  }

  /**
   * @param proceedingJoinPoint proceedingJoinPoint
   * @param apiType apiType
   * @param detailMessage detailMessage
   * @return Object
   * @throws Throwable throwable
   */
  private Object logAroundBean(
      ProceedingJoinPoint proceedingJoinPoint, LoggingBean.ApiType apiType, String detailMessage)
      throws Throwable {
    long startTime = System.currentTimeMillis();
    Object object = proceedingJoinPoint.proceed();
    long endTime = System.currentTimeMillis();

    CodeSignature signature = (CodeSignature) proceedingJoinPoint.getSignature();

    LoggingBean bean =
        LoggingBean.builder()
            .apiType(apiType)
            .className(proceedingJoinPoint.getTarget().getClass().getSimpleName())
            .method(signature.getName())
            .parameters(signature.getParameterNames())
            .arguments(proceedingJoinPoint.getArgs())
            .returnValue(object)
            .durationMs(endTime - startTime)
            .detailMessage(detailMessage)
            .build();
    log.info(bean.toString());
    return object;
  }
}
