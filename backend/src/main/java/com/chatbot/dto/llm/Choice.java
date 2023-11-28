package com.chatbot.dto.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import lombok.Data;

@Data
public class Choice {
  @JsonProperty
  private LinkedHashMap<String, String> message;
}
