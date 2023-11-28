package com.chatbot.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.dto.sync.DateRangeRequest;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DateRangeValidatorTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private ConstraintValidatorContext context;

  private final DateRangeValidator validator = new DateRangeValidator();

  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

  @Test
  void acceptsPastRange() {
    DateRangeRequest range = new DateRangeRequest("01-01-2020", "31-01-2020");
    assertThat(validator.isValid(range, context)).isTrue();
  }

  @Test
  void acceptsRangeEndingYesterday() {
    String yesterday = LocalDate.now().minusDays(1).format(FMT);
    DateRangeRequest range = new DateRangeRequest("01-01-2020", yesterday);
    assertThat(validator.isValid(range, context)).isTrue();
  }

  @Test
  void rejectsRangeEndingToday() {
    String today = LocalDate.now().format(FMT);
    DateRangeRequest range = new DateRangeRequest("01-01-2020", today);
    assertThat(validator.isValid(range, context)).isFalse();
  }

  @Test
  void rejectsFutureEndDate() {
    DateRangeRequest range = new DateRangeRequest("01-01-2020", "01-01-2099");
    assertThat(validator.isValid(range, context)).isFalse();
  }

  @Test
  void rejectsStartAfterEnd() {
    DateRangeRequest range = new DateRangeRequest("31-01-2020", "01-01-2020");
    assertThat(validator.isValid(range, context)).isFalse();
  }

  @Test
  void rejectsBadFormat() {
    DateRangeRequest range = new DateRangeRequest("2020-01-01", "2020-01-31");
    assertThat(validator.isValid(range, context)).isFalse();
  }

  @Test
  void rejectsImpossibleDate() {
    DateRangeRequest range = new DateRangeRequest("31-02-2020", "31-03-2020");
    assertThat(validator.isValid(range, context)).isFalse();
  }

  @Test
  void blankFieldsAreLeftToNotBlank() {
    assertThat(validator.isValid(new DateRangeRequest("", "01-01-2020"), context)).isTrue();
    assertThat(validator.isValid(new DateRangeRequest("01-01-2020", null), context)).isTrue();
    assertThat(validator.isValid(null, context)).isTrue();
  }
}
