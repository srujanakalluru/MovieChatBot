/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.dto.openai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.LinkedHashMap;

@Data
public class Choice {
  @JsonProperty
  private LinkedHashMap<String,String> message;
}
