package com.chatbot.errorhandling;

import com.chatbot.constant.ApiMessages;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private final MessageSource messageSource;

  @ExceptionHandler(BindException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseEntity<GlobalError> handleBindException(BindException exception) {
    String reason = exception.getBindingResult().getAllErrors().stream()
        .map(DefaultMessageSourceResolvable::getDefaultMessage)
        .findFirst()
        .orElse(exception.getMessage());
    return badRequest(reason);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseEntity<GlobalError> handleConstraintViolationException(
      ConstraintViolationException exception) {
    String reason = exception.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .findFirst()
        .orElse(exception.getMessage());
    return badRequest(reason);
  }

  private ResponseEntity<GlobalError> badRequest(String reason) {
    GlobalError error =
        new GlobalError(
            HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST, reason, LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(value = HttpStatus.BAD_REQUEST)
  public ResponseEntity<GlobalError> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException methodArgumentTypeMismatchException) {
    GlobalError error =
        new GlobalError(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST,
            methodArgumentTypeMismatchException.getMessage(),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(LlmUnavailableException.class)
  @ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
  public ResponseEntity<GlobalError> handleLlmUnavailable(LlmUnavailableException exception) {
    GlobalError error =
        new GlobalError(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            HttpStatus.SERVICE_UNAVAILABLE,
            msg(ApiMessages.LLM_UNAVAILABLE),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
  public ResponseEntity<GlobalError> handleUnhandledException(Exception exception) {
    GlobalError error =
        new GlobalError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR,
            msg(ApiMessages.INTERNAL_ERROR),
            LocalDateTime.now());
    return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private String msg(String code) {
    return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
  }
}
