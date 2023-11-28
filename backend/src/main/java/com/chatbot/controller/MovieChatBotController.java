package com.chatbot.controller;

import com.chatbot.constant.ApiMessages;
import com.chatbot.dto.JsonObjectWrapper;
import com.chatbot.service.LlmService;
import com.chatbot.service.MovieIntelService;
import com.chatbot.service.SyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Slf4j
@Tag(name = "Movie Chat", description = "Natural-language movie queries")
public class MovieChatBotController {

  private final MovieIntelService movieIntelService;
  private final LlmService llmService;
  private final SyncService syncService;
  private final MessageSource messageSource;

  @Autowired
  public MovieChatBotController(MovieIntelService movieIntelService,
                                LlmService llmService,
                                SyncService syncService,
                                MessageSource messageSource) {
    this.movieIntelService = movieIntelService;
    this.llmService = llmService;
    this.syncService = syncService;
    this.messageSource = messageSource;
  }

  @PostMapping("/query")
  public ResponseEntity<?> query(
      @NotBlank(message = "{query.empty}") @RequestBody(required = false) String userInput) {
    if (syncService.isSyncInProgress()) {
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", msg(ApiMessages.SYNC_IN_PROGRESS)));
    }

    String sql = llmService.generateSql(userInput.trim());

    if (sql == null || sql.isBlank()) {
      log.warn("Empty SQL for input: {}", userInput);
      return ResponseEntity.ok(Map.of("message", msg(ApiMessages.NO_RESULTS)));
    }

    JsonObjectWrapper result = movieIntelService.searchByCriteria(sql);
    return ResponseEntity.ok(result);
  }

  private String msg(String code) {
    return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
  }
}
