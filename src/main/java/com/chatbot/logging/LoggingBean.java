/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.logging;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Builder
@AllArgsConstructor
public class LoggingBean {
  private ApiType apiType;
  private String className;
  private String method;
  private Object[] arguments;
  private String[] parameters;
  private Long durationMs;
  private String detailMessage;
  private String stackTrace;
  private Object returnValue;

  /**
   * @return String
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("%-11s", apiType)).append("=\t");
    sb.append("{");
    sb.append("className =\"")
        .append(String.format("%-40s", className))
        .append("\"")
        .append(" | method =\"")
        .append(String.format("%-20s", method))
        .append("\"");

    if (null != durationMs)
      sb.append(" | \tdurationMs =\"").append(String.format("%3d", durationMs)).append("\"");

    if (null != parameters) {
      sb.append(" | \tparameters =\"")
          .append(Arrays.toString(parameters))
          .append("\"")
          .append(" | \targuments =\"")
          .append(Arrays.toString(arguments))
          .append("\"");
    }

    if (StringUtils.hasLength(stackTrace)) {
      sb.append(" | \tstacktrace =\"").append(stackTrace.trim()).append("\"");
    }

    if (null != detailMessage) {
      sb.append(" | \tdetailMessage =\"").append(detailMessage).append("\"");
    }
    sb.append("}");
    return sb.toString();
  }

  @Getter
  public enum ApiType {
    EXTERNAL,
    CONTROLLER,
    SERVICE,
    REPOSITORY,
    EXCEPTIONHANDLER,
    SCHEDULER,
    ERROR
  }
}
