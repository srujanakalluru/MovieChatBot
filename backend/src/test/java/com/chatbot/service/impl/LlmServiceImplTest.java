package com.chatbot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.config.LlmConfig;
import com.chatbot.dto.llm.Choice;
import com.chatbot.dto.llm.LlmRequest;
import com.chatbot.dto.llm.LlmResponse;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LlmServiceImplTest {

  @Mock private LlmConfig config;
  @Mock private RestTemplate llmRestTemplate;
  private LlmServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new LlmServiceImpl(config, llmRestTemplate);
  }

  private void stubConfig() {
    when(config.getModel()).thenReturn("model-x");
    when(config.getMaxTokens()).thenReturn(300);
    when(config.getTemperature()).thenReturn(0.05);
    when(config.getEndpoint()).thenReturn("http://llm");
  }

  private ResponseEntity<LlmResponse> responseWithContent(String content) {
    LinkedHashMap<String, String> message = new LinkedHashMap<>();
    message.put("content", content);
    Choice choice = new Choice();
    choice.setMessage(message);
    LlmResponse body = new LlmResponse();
    body.setChoices(List.of(choice));
    return new ResponseEntity<>(body, HttpStatus.OK);
  }

  @Test
  void returnsSqlFromModelAndSendsSystemPlusUserMessages() {
    stubConfig();
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenReturn(responseWithContent("SELECT 1 FROM movie;"));

    String sql = service.generateSql("top movies");

    assertThat(sql).isEqualTo("SELECT 1 FROM movie;");

    ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
    verify(llmRestTemplate)
        .postForEntity(eq("http://llm"), requestCaptor.capture(), eq(LlmResponse.class));
    LlmRequest sent = requestCaptor.getValue();
    assertThat(sent.getModel()).isEqualTo("model-x");
    assertThat(sent.getMaxTokens()).isEqualTo(300);
    assertThat(sent.getTemperature()).isEqualTo(0.05);
    assertThat(sent.getMessages()).hasSize(2);
    assertThat(sent.getMessages().get(0).getRole()).isEqualTo("system");
    assertThat(sent.getMessages().get(1).getRole()).isEqualTo("user");
    assertThat(sent.getMessages().get(1).getContent()).isEqualTo("top movies");
  }

  @Test
  void stripsNullsOrderingAndClosingSqlTag() {
    stubConfig();
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenReturn(responseWithContent("SELECT a FROM b NULLS LAST [/SQL] junk"));

    assertThat(service.generateSql("q")).isEqualTo("SELECT a FROM b");
  }

  @Test
  void returnsNullWhenStatusNotOk() {
    stubConfig();
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenReturn(new ResponseEntity<>((LlmResponse) null, HttpStatus.INTERNAL_SERVER_ERROR));

    assertThat(service.generateSql("q")).isNull();
  }

  @Test
  void returnsNullWhenBodyIsNull() {
    stubConfig();
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenReturn(new ResponseEntity<>((LlmResponse) null, HttpStatus.OK));

    assertThat(service.generateSql("q")).isNull();
  }

  @Test
  void returnsNullWhenChoicesAreEmpty() {
    stubConfig();
    LlmResponse body = new LlmResponse();
    body.setChoices(List.of());
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

    assertThat(service.generateSql("q")).isNull();
  }

  @Test
  void unreachableEndpointThrowsLlmUnavailable() {
    stubConfig();
    when(llmRestTemplate.postForEntity(eq("http://llm"), any(LlmRequest.class), eq(LlmResponse.class)))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("connection refused"));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.generateSql("q"))
        .isInstanceOf(com.chatbot.errorhandling.LlmUnavailableException.class);
  }
}
