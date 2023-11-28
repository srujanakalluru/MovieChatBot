package com.chatbot.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedBatchMessage implements Serializable {

  private String startDate;
  private String endDate;
  private int attemptCount;
  private LocalDateTime firstFailedAt;
  private String lastError;
}
