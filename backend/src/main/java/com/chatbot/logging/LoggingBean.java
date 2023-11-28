package com.chatbot.logging;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.util.StringUtils;

@Builder
@AllArgsConstructor
public class LoggingBean {
  private String method;
  private Object[] arguments;
  private String[] parameters;
  private Long durationMs;
  private String detailMessage;

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("method=").append(method);
    if (durationMs != null) {
      sb.append(" durationMs=").append(durationMs);
    }
    if (parameters != null) {
      sb.append(" params=").append(Arrays.toString(parameters));
      if (arguments != null) {
        sb.append(" args=")
            .append(
                Arrays.stream(arguments)
                    .map(this::formatArg)
                    .collect(Collectors.joining(", ", "[", "]")));
      } else {
        sb.append(" args=[redacted]");
      }
    }
    if (StringUtils.hasLength(detailMessage)) {
      sb.append(" detail=").append(detailMessage);
    }
    return sb.toString();
  }

  private String formatArg(Object arg) {
    if (arg == null) return "null";
    if (arg instanceof Collection<?> c) {
      return c.getClass().getSimpleName() + "[" + c.size() + "]";
    }
    if (arg.getClass().isArray()) {
      return arg.getClass().getComponentType().getSimpleName() + "[" + Array.getLength(arg) + "]";
    }
    String s = String.valueOf(arg);
    return s.length() > 80 ? s.substring(0, 80) + "…" : s;
  }

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
