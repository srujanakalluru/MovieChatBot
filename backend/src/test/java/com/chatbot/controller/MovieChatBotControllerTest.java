package com.chatbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.dto.JsonObjectWrapper;
import com.chatbot.service.LlmService;
import com.chatbot.service.MovieIntelService;
import com.chatbot.service.SyncService;
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
class MovieChatBotControllerTest {

  @Mock private MovieIntelService movieIntelService;
  @Mock private LlmService llmService;
  @Mock private SyncService syncService;
  @Mock private org.springframework.context.MessageSource messageSource;
  @InjectMocks private MovieChatBotController controller;

  @org.junit.jupiter.api.BeforeEach
  void stubMessages() {
    org.mockito.Mockito.lenient()
        .when(
            messageSource.getMessage(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(java.util.Locale.class)))
        .thenReturn("msg");
  }

  @Test
  void syncInProgressReturns503() {
    when(syncService.isSyncInProgress()).thenReturn(true);

    ResponseEntity<?> response = controller.query("top movies");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    verify(llmService, never()).generateSql(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void nullSqlReturnsFriendlyMessage() {
    when(syncService.isSyncInProgress()).thenReturn(false);
    when(llmService.generateSql("top movies")).thenReturn(null);

    ResponseEntity<?> response = controller.query("top movies");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).asInstanceOf(MAP).containsKey("message");
  }

  @Test
  void blankSqlReturnsFriendlyMessage() {
    when(syncService.isSyncInProgress()).thenReturn(false);
    when(llmService.generateSql("top movies")).thenReturn("   ");

    ResponseEntity<?> response = controller.query("top movies");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isInstanceOf(Map.class);
  }

  @Test
  void validSqlReturnsResults() {
    JsonObjectWrapper wrapper = new JsonObjectWrapper(List.of());
    when(syncService.isSyncInProgress()).thenReturn(false);
    when(llmService.generateSql("top movies")).thenReturn("SELECT 1");
    when(movieIntelService.searchByCriteria("SELECT 1")).thenReturn(wrapper);

    ResponseEntity<?> response = controller.query("top movies");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(wrapper);
  }
}
