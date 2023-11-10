/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class OpenAiRequest {

  @JsonProperty private String model;
  @JsonProperty private List<Message> messages;
  @JsonProperty private boolean stream = false;

  @JsonProperty("max_tokens")
  private Integer maxTokens;

  @JsonProperty private Double temperature;

  public OpenAiRequest(
      String model, List<Message> messages, Integer maxTokens, Double temperature) {
    this.model = model;
    this.messages = messages;
    this.maxTokens = maxTokens;
    this.temperature = temperature;
  }
}
