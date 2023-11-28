package com.chatbot.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class LlmRequest {

  @JsonProperty private String model;
  @JsonProperty private List<Message> messages;
  @JsonProperty private boolean stream = false;

  @JsonProperty("max_tokens")
  private int maxTokens;

  @JsonProperty private double temperature;

  public LlmRequest(String model, List<Message> messages, int maxTokens, double temperature) {
    this.model = model;
    this.messages = messages;
    this.maxTokens = maxTokens;
    this.temperature = temperature;
  }
}
