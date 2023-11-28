package com.chatbot.utils;

import static com.chatbot.constant.Constants.*;

import java.time.LocalDate;

public class DateConversionUtils {
  public static LocalDate convertStringToDate(String dateStr) {
    return LocalDate.parse(dateStr, DATE_FORMATTER);
  }
}
