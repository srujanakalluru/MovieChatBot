package com.chatbot.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

  @Mock private MessageSource messageSource;

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler(messageSource);
    lenient()
        .when(messageSource.getMessage(eq("error.internal"), any(), any(Locale.class)))
        .thenReturn("An unexpected error occurred");
    lenient()
        .when(messageSource.getMessage(eq("llm.unavailable"), any(), any(Locale.class)))
        .thenReturn("Model unreachable");
  }

  @Test
  void typeMismatchMapsToBadRequest() {
    MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
    when(ex.getMessage()).thenReturn("bad type");

    ResponseEntity<GlobalError> response =
        handler.handleMethodArgumentTypeMismatchException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode()).isEqualTo(400);
    assertThat(response.getBody().getErrorReason()).isEqualTo("bad type");
  }

  @Test
  void bindExceptionMapsToBadRequestWithFieldMessage() {
    BindException ex = new BindException(new Object(), "request");
    ex.addError(
        new org.springframework.validation.ObjectError("request", "Dates must be valid"));

    ResponseEntity<GlobalError> response = handler.handleBindException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorReason()).isEqualTo("Dates must be valid");
  }

  @Test
  void constraintViolationMapsToBadRequestWithMessage() {
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
    when(violation.getMessage()).thenReturn("Query must not be empty");
    ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

    ResponseEntity<GlobalError> response = handler.handleConstraintViolationException(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorReason()).isEqualTo("Query must not be empty");
  }

  @Test
  void llmUnavailableMapsTo503WithFriendlyMessage() {
    ResponseEntity<GlobalError> response =
        handler.handleLlmUnavailable(
            new LlmUnavailableException("connection refused", new RuntimeException()));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorReason()).isEqualTo("Model unreachable");
  }

  @Test
  void unhandledExceptionMapsTo500WithoutLeakingDetails() {
    ResponseEntity<GlobalError> response =
        handler.handleUnhandledException(
            new Exception("jdbc:mysql://internal-host:3306/secret-db exploded"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getErrorCode()).isEqualTo(500);
    assertThat(response.getBody().getErrorReason()).isEqualTo("An unexpected error occurred");
    assertThat(response.getBody().getErrorReason()).doesNotContain("jdbc:mysql");
  }
}
