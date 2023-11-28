package com.chatbot.dto.sync;

import com.chatbot.validation.ValidDateRange;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class DateRangeRequest {

  @NotBlank(message = "{validation.dateRange.required}")
  private String startDateStr;

  @NotBlank(message = "{validation.dateRange.required}")
  private String endDateStr;
}
