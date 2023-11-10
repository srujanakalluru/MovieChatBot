/*
 * Copyright (c) 2023.
 * <p>Author: Srujana Kalluru </p>
 */

package com.chatbot.service;

import org.springframework.stereotype.Service;

@Service
public interface OpenAiService {
  String submitOpenAiRequest(String userInput);
}
