package com.chatbot.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class DateConversionUtilsTest {

  @Test
  void parsesDateInDdMmYyyyFormat() {
    assertThat(DateConversionUtils.convertStringToDate("09-06-2026"))
        .isEqualTo(LocalDate.of(2026, 6, 9));
  }

  @Test
  void throwsOnWrongFormat() {
    assertThatThrownBy(() -> DateConversionUtils.convertStringToDate("2026-06-09"))
        .isInstanceOf(DateTimeParseException.class);
  }

  @Test
  void throwsOnGarbage() {
    assertThatThrownBy(() -> DateConversionUtils.convertStringToDate("not-a-date"))
        .isInstanceOf(DateTimeParseException.class);
  }
}
