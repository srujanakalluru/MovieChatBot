package com.chatbot.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

@Data
public class LlmResponse {

  @JsonProperty
  private List<Choice> choices;
}
