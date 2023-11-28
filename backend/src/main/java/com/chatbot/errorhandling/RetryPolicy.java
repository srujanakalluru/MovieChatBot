package com.chatbot.errorhandling;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

public final class RetryPolicy {

  private RetryPolicy() {}

  public static boolean isRetryable(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof ResourceAccessException
          || t instanceof HttpServerErrorException
          || t instanceof HttpClientErrorException.TooManyRequests
          || t instanceof TransientDataAccessException) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
