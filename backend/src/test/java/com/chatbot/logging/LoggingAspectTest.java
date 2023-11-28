package com.chatbot.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.chatbot.errorhandling.NonRetryableException;
import com.chatbot.repository.MovieRepository;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.CodeSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

@ExtendWith(MockitoExtension.class)
class LoggingAspectTest {

  @Mock private ProceedingJoinPoint pjp;
  @Mock private CodeSignature signature;
  @Mock private JoinPoint joinPoint;

  private final LoggingAspect aspect = new LoggingAspect();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubProceedingJoinPoint() {
    when(pjp.getSignature()).thenReturn(signature);
    Mockito.lenient().when(signature.getName()).thenReturn("doWork");
    Mockito.lenient().when(signature.getParameterNames()).thenReturn(new String[] {"arg"});
    Mockito.lenient().when((Class) signature.getDeclaringType()).thenReturn(Object.class);
    Mockito.lenient().when(pjp.getArgs()).thenReturn(new Object[] {"value"});
  }

  @Test
  void logAroundReturnsTargetResultAndCleansMdc() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");

    Object result = aspect.logAroundService(pjp);

    assertThat(result).isEqualTo("result");
    assertThat(MDC.get(LoggingAspect.LAYER)).isNull();
  }

  @Test
  void logAroundPropagatesExceptionAndCleansMdc() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

    assertThatThrownBy(() -> aspect.logAroundController(pjp))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("boom");
    assertThat(MDC.get(LoggingAspect.LAYER)).isNull();
  }

  @Test
  void logAroundRestoresOuterLayer() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");
    MDC.put(LoggingAspect.LAYER, "CONTROLLER");

    aspect.logAroundExternalService(pjp);

    assertThat(MDC.get(LoggingAspect.LAYER)).isEqualTo("CONTROLLER");
  }

  @Test
  void repositoryLayerResolvesRepositoryInterfaceLogger() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");
    when(pjp.getTarget()).thenReturn(Mockito.mock(MovieRepository.class));

    Object result = aspect.logAroundRepository(pjp);

    assertThat(result).isEqualTo("result");
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private void stubJoinPoint() {
    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getName()).thenReturn("doWork");
    when(signature.getParameterNames()).thenReturn(new String[] {"arg"});
    when((Class) signature.getDeclaringType()).thenReturn(Object.class);
  }

  @Test
  void afterThrowingLogsNonRetryableAsWarning() {
    stubJoinPoint();

    aspect.logAfterThrowing(joinPoint, new NonRetryableException("overlap"));

    assertThat(MDC.get(LoggingAspect.LAYER)).isNull();
  }

  @Test
  void afterThrowingLogsGenericExceptionAsError() {
    stubJoinPoint();

    aspect.logAfterThrowing(joinPoint, new RuntimeException((String) null));

    assertThat(MDC.get(LoggingAspect.LAYER)).isNull();
  }

  @Test
  void afterThrowingFallsBackToCauseMessage() {
    stubJoinPoint();

    aspect.logAfterThrowing(
        joinPoint, new RuntimeException(null, new IllegalStateException("root cause")));

    assertThat(MDC.get(LoggingAspect.LAYER)).isNull();
  }

  @Test
  void controllerAndSchedulerWrappersDelegate() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");

    assertThat(aspect.logAroundController(pjp)).isEqualTo("result");
    assertThat(aspect.logAroundScheduler(pjp)).isEqualTo("result");
  }

  @Test
  void nestedCallKeepsOuterCorrelationId() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");
    MDC.put(com.chatbot.filter.CorrelationIdFilter.CORRELATION_ID, "abc12345");

    aspect.logAroundService(pjp);

    assertThat(MDC.get(com.chatbot.filter.CorrelationIdFilter.CORRELATION_ID))
        .isEqualTo("abc12345");
  }

  @Test
  void repositoryLayerWithNullTargetFallsBackToDeclaringType() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");
    when(pjp.getTarget()).thenReturn(null);

    assertThat(aspect.logAroundRepository(pjp)).isEqualTo("result");
  }

  @Test
  void repositoryLayerWithoutRepositoryInterfaceFallsBackToDeclaringType() throws Throwable {
    stubProceedingJoinPoint();
    when(pjp.proceed()).thenReturn("result");
    when(pjp.getTarget()).thenReturn("plain string target");

    assertThat(aspect.logAroundRepository(pjp)).isEqualTo("result");
  }

  @Test
  void pointcutMarkersAreNoOps() {
    aspect.controllerPointCut();
    aspect.repositoryPointCut();
    aspect.servicePointCut();
    aspect.externalServicePointCut();
    aspect.schedulerPointCut();
  }
}
