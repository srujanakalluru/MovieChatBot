package com.chatbot.errorhandling;

public class LlmUnavailableException extends RuntimeException {

  public LlmUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
