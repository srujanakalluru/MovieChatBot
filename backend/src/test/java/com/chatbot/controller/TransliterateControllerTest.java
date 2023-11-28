package com.chatbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chatbot.service.TransliterateService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class TransliterateControllerTest {

  @Mock private TransliterateService transliterateService;
  @InjectMocks private TransliterateController controller;

  @Test
  void returnsSuggestionsFromService() {
    when(transliterateService.transliterate("evaru", "te"))
        .thenReturn(List.of("ఎవరు"));

    ResponseEntity<Map<String, Object>> response = controller.transliterate("evaru", "te");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .containsEntry("suggestions", List.of("ఎవరు"));
  }

  @Test
  void emptySuggestionsStillReturn200() {
    when(transliterateService.transliterate("xyz", "te")).thenReturn(List.of());

    ResponseEntity<Map<String, Object>> response = controller.transliterate("xyz", "te");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("suggestions", List.of());
  }
}
