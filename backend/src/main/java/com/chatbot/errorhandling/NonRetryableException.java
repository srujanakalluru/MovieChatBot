package com.chatbot.errorhandling;

public class NonRetryableException extends RuntimeException {
  public NonRetryableException(String message) {
    super(message);
  }
}
