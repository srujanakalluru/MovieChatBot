/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.dto.openai;

import lombok.*;

@Data
@AllArgsConstructor
public class Message {
  private String role;
  private String content;
}
