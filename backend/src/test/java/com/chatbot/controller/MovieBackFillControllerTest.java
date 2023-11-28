package com.chatbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.beans.SyncLog;
import com.chatbot.dto.sync.DateRangeRequest;
import com.chatbot.dto.sync.SyncStatusResponse;
import com.chatbot.service.MovieBackFillService;
import com.chatbot.service.SyncService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MovieBackFillControllerTest {

  @Mock private MovieBackFillService movieBackfillService;
  @Mock private SyncService syncService;
  @Mock private org.springframework.context.MessageSource messageSource;
  @InjectMocks private MovieBackFillController controller;

  @BeforeEach
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
  void getSyncStatusIncludesInProgressFlag() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.of(2024, 1, 1));
    when(syncService.getStatus()).thenReturn(log);
    when(syncService.isSyncInProgress()).thenReturn(true);

    SyncStatusResponse response = controller.getSyncStatus();

    assertThat(response.lastSyncDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(response.syncInProgress()).isTrue();
  }

  @Test
  void syncStartsAsyncAndReturns202() {
    when(syncService.isSyncInProgress()).thenReturn(false);

    ResponseEntity<?> response = controller.sync();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getBody()).asInstanceOf(MAP).containsKey("message");
    verify(syncService).syncNowAsync();
  }

  @Test
  void syncWhileRunningReturns409WithoutStartingAnother() {
    when(syncService.isSyncInProgress()).thenReturn(true);

    ResponseEntity<?> response = controller.sync();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).asInstanceOf(MAP).containsKey("error");
    verify(syncService, never()).syncNowAsync();
  }

  @Test
  void syncRangeDelegatesToBackfill() {
    controller.syncRange(new DateRangeRequest("01-01-2020", "31-01-2020"));
    verify(movieBackfillService).addMoviesToDatabase("01-01-2020", "31-01-2020");
  }

  @Test
  void backfillLanguageDataDelegatesToBackfill() {
    controller.addLanguagesToDatabase();
    verify(movieBackfillService).addLanguagesToDatabase();
  }

  @Test
  void backfillGenreDataDelegatesToBackfill() {
    controller.addGenreToDatabase();
    verify(movieBackfillService).addGenresToDatabase();
  }
}
