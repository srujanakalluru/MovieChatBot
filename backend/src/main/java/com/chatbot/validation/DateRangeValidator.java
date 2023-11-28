package com.chatbot.validation;

import com.chatbot.dto.sync.DateRangeRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, DateRangeRequest> {

  private static final DateTimeFormatter FORMAT =
      DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT);

  @Override
  public boolean isValid(DateRangeRequest range, ConstraintValidatorContext context) {
    if (range == null
        || isBlank(range.getStartDateStr())
        || isBlank(range.getEndDateStr())) {
      return true;
    }

    LocalDate start;
    LocalDate end;
    try {
      start = LocalDate.parse(range.getStartDateStr(), FORMAT);
      end = LocalDate.parse(range.getEndDateStr(), FORMAT);
    } catch (DateTimeParseException e) {
      return violation(context, "{validation.dateRange.format}");
    }

    if (start.isAfter(end)) {
      return violation(context, "{validation.dateRange.order}");
    }

    LocalDate yesterday = LocalDate.now().minusDays(1);
    if (end.isAfter(yesterday)) {
      return violation(context, "{validation.dateRange.tooLate}");
    }

    return true;
  }

  private boolean violation(ConstraintValidatorContext context, String template) {
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(template).addConstraintViolation();
    return false;
  }

  private boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
