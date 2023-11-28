package com.chatbot.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

class RetryPolicyTest {

  @Test
  void networkFailureIsRetryable() {
    assertThat(RetryPolicy.isRetryable(new ResourceAccessException("connection reset"))).isTrue();
  }

  @Test
  void serverErrorIsRetryable() {
    assertThat(RetryPolicy.isRetryable(
            new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)))
        .isTrue();
  }

  @Test
  void rateLimitIsRetryable() {
    assertThat(RetryPolicy.isRetryable(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "429", HttpHeaders.EMPTY, new byte[0], null)))
        .isTrue();
  }

  @Test
  void transientDatabaseErrorIsRetryable() {
    assertThat(RetryPolicy.isRetryable(new QueryTimeoutException("timeout"))).isTrue();
  }

  @Test
  void wrappedTransientCauseIsRetryable() {
    RuntimeException wrapped =
        new RuntimeException("wrapper", new ResourceAccessException("io"));
    assertThat(RetryPolicy.isRetryable(wrapped)).isTrue();
  }

  @Test
  void unknownExceptionIsNotRetryable() {
    assertThat(RetryPolicy.isRetryable(new RuntimeException("data bug"))).isFalse();
    assertThat(RetryPolicy.isRetryable(new IllegalStateException("logic error"))).isFalse();
  }

  @Test
  void explicitNonRetryableIsNotRetryable() {
    assertThat(RetryPolicy.isRetryable(new NonRetryableException("overlap"))).isFalse();
  }

  @Test
  void clientErrorOtherThanRateLimitIsNotRetryable() {
    assertThat(RetryPolicy.isRetryable(
            HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "404", HttpHeaders.EMPTY, new byte[0], null)))
        .isFalse();
  }
}
