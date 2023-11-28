package com.chatbot.controller;

import com.chatbot.beans.SyncLog;
import com.chatbot.constant.ApiMessages;
import com.chatbot.dto.sync.DateRangeRequest;
import com.chatbot.dto.sync.SyncStatusResponse;
import com.chatbot.service.MovieBackFillService;
import com.chatbot.service.SyncService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/internal")
@RestController
@Hidden
public class MovieBackFillController {

  private final MovieBackFillService movieBackfillService;
  private final SyncService syncService;
  private final MessageSource messageSource;

  @Autowired
  public MovieBackFillController(MovieBackFillService movieBackfillService,
                                 SyncService syncService,
                                 MessageSource messageSource) {
    this.movieBackfillService = movieBackfillService;
    this.syncService = syncService;
    this.messageSource = messageSource;
  }

  @Operation(tags = "Sync", summary = "Current sync status and last sync time")
  @GetMapping("/sync-status")
  public SyncStatusResponse getSyncStatus() {
    SyncLog status = syncService.getStatus();
    return new SyncStatusResponse(
        status.getLastSyncDate(), status.getSyncedAt(), syncService.isSyncInProgress());
  }

  @Operation(tags = "Sync", summary = "Start a full sync in the background")
  @PostMapping("/sync")
  public ResponseEntity<Map<String, String>> sync() {
    if (syncService.isSyncInProgress()) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", msg(ApiMessages.SYNC_IN_PROGRESS)));
    }
    syncService.syncNowAsync();
    return ResponseEntity.accepted().body(Map.of("message", msg(ApiMessages.SYNC_STARTED)));
  }

  @Operation(tags = "Backfill", summary = "Backfill movies for a date range")
  @PostMapping("/sync/range")
  public void syncRange(@Valid DateRangeRequest range) {
    movieBackfillService.addMoviesToDatabase(range.getStartDateStr(), range.getEndDateStr());
  }

  @Operation(tags = "Backfill", summary = "Backfill language reference data")
  @PostMapping("/backfill_language_data")
  public void addLanguagesToDatabase() {
    movieBackfillService.addLanguagesToDatabase();
  }

  @Operation(tags = "Backfill", summary = "Backfill genre reference data")
  @PostMapping("/backfill_genre_data")
  public void addGenreToDatabase() {
    movieBackfillService.addGenresToDatabase();
  }

  private String msg(String code) {
    return messageSource.getMessage(code, null, LocaleContextHolder.getLocale());
  }
}
