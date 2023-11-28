package com.chatbot.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoggingBeanTest {

  @Test
  void rendersBodyWithoutLayerTagOrTabs() {
    String line =
        LoggingBean.builder()
            .method("generateSql")
            .parameters(new String[] {"userInput"})
            .arguments(new Object[] {"top movies"})
            .durationMs(4944L)
            .build()
            .toString();

    assertThat(line)
        .startsWith("method=generateSql")
        .contains("durationMs=4944")
        .contains("params=[userInput]")
        .contains("args=[top movies]")
        .doesNotContain("[SERVICE")
        .doesNotContain("\t");
  }

  @Test
  void summarisesCollectionArgumentsAndOmitsDurationWhenNull() {
    String line =
        LoggingBean.builder()
            .method("findExistingDates")
            .parameters(new String[] {"dateList"})
            .arguments(new Object[] {new ArrayList<>(List.of(1, 2, 3))})
            .build()
            .toString();

    assertThat(line).startsWith("method=findExistingDates").contains("args=[ArrayList[3]]");
    assertThat(line).doesNotContain("durationMs");
  }

  @Test
  void nullArgumentsRenderAsRedacted() {
    String line =
        LoggingBean.builder()
            .method("google")
            .parameters(new String[] {"request"})
            .arguments(null)
            .durationMs(12L)
            .build()
            .toString();

    assertThat(line)
        .contains("params=[request]")
        .contains("args=[redacted]");
  }

  @Test
  void truncatesLongArguments() {
    String longArg = "x".repeat(200);
    String line =
        LoggingBean.builder()
            .method("query")
            .parameters(new String[] {"userInput"})
            .arguments(new Object[] {longArg})
            .durationMs(1L)
            .build()
            .toString();

    assertThat(line).startsWith("method=query").contains("…");
    assertThat(line).doesNotContain("x".repeat(120));
  }
}
