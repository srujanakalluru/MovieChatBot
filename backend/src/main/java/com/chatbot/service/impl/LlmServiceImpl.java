package com.chatbot.service.impl;

import com.chatbot.config.LlmConfig;
import com.chatbot.constant.LlmPrompts;
import com.chatbot.errorhandling.LlmUnavailableException;
import com.chatbot.dto.llm.LlmRequest;
import com.chatbot.dto.llm.LlmResponse;
import com.chatbot.dto.llm.Message;
import com.chatbot.service.LlmService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class LlmServiceImpl implements LlmService {

  private final LlmConfig config;
  private final RestTemplate llmRestTemplate;

  @Autowired
  public LlmServiceImpl(LlmConfig config, RestTemplate llmRestTemplate) {
    this.config = config;
    this.llmRestTemplate = llmRestTemplate;
  }

  private static final int MAX_GENERATED_SQL_CHARS = 8192;

  private String sanitizeSql(String sql) {
    return sql
        .replaceAll("\\[/SQL\\].*", "")
        .replaceAll("(?i)\\s++NULLS\\s++(LAST|FIRST)", "")
        .trim();
  }

  @Override
  public String generateSql(String userInput) {
    List<Message> messages = List.of(
        new Message("system", LlmPrompts.SYSTEM_PROMPT),
        new Message("user", userInput)
    );

    LlmRequest request = new LlmRequest(
        config.getModel(),
        messages,
        config.getMaxTokens(),
        config.getTemperature()
    );

    log.info("{}", userInput);

    ResponseEntity<LlmResponse> response;
    try {
      response = llmRestTemplate.postForEntity(config.getEndpoint(), request, LlmResponse.class);
    } catch (RestClientException e) {
      throw new LlmUnavailableException("LLM endpoint unreachable: " + e.getMessage(), e);
    }

    LlmResponse body = response.getBody();
    if (!HttpStatus.OK.equals(response.getStatusCode())
        || body == null
        || body.getChoices() == null
        || body.getChoices().isEmpty()
        || body.getChoices().get(0).getMessage() == null) {
      log.warn("No usable response - status={}", response.getStatusCode());
      return null;
    }

    String content = body.getChoices().get(0).getMessage().get("content");
    if (content == null) {
      return null;
    }
    if (content.length() > MAX_GENERATED_SQL_CHARS) {
      log.warn("LLM response exceeded {} chars ({}) - discarding", MAX_GENERATED_SQL_CHARS, content.length());
      return null;
    }
    return sanitizeSql(content);
  }
}
