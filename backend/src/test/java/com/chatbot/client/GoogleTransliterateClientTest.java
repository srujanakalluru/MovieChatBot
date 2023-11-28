package com.chatbot.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GoogleTransliterateClientTest {

  @Mock private RestTemplate restTemplate;

  private GoogleTransliterateClient client;

  @BeforeEach
  void setUp() {
    client = new GoogleTransliterateClient(restTemplate, JsonMapper.builder().build());
    ReflectionTestUtils.setField(client, "enabled", true);
    ReflectionTestUtils.setField(client, "endpoint", "https://example.test/request");
  }

  @Test
  void parsesSuggestionsFromSuccessResponse() {
    String body =
        "[\"SUCCESS\",[[\"evaru\",[\"ఎవరు\",\"ఎవరూ\"]]]]";
    when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(body);

    List<String> result = client.transliterate("evaru", "te");

    assertThat(result).containsExactly("ఎవరు", "ఎవరూ");
  }

  @Test
  void nonSuccessStatusReturnsEmpty() {
    when(restTemplate.getForObject(anyString(), eq(String.class)))
        .thenReturn("[\"FAILURE\",[]]");

    assertThat(client.transliterate("evaru", "te")).isEmpty();
  }

  @Test
  void httpFailureReturnsEmpty() {
    when(restTemplate.getForObject(anyString(), eq(String.class)))
        .thenThrow(new RestClientException("timeout"));

    assertThat(client.transliterate("evaru", "te")).isEmpty();
  }

  @Test
  void blankTextReturnsEmptyWithoutHttpCall() {
    assertThat(client.transliterate("   ", "te")).isEmpty();
  }

  @Test
  void disabledFlagReturnsEmptyWithoutHttpCall() {
    ReflectionTestUtils.setField(client, "enabled", false);

    assertThat(client.transliterate("evaru", "te")).isEmpty();
  }
}
