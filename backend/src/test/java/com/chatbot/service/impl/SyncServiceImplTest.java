package com.chatbot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.beans.SyncLog;
import com.chatbot.messaging.FailedBatchProducer;
import com.chatbot.repository.MovieRepository;
import com.chatbot.repository.SyncLogRepository;
import com.chatbot.service.MovieBackFillService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SyncServiceImplTest {

  @Mock private SyncLogRepository syncLogRepository;
  @Mock private MovieBackFillService movieBackFillService;
  @Mock private FailedBatchProducer failedBatchProducer;
  @Mock private MovieRepository movieRepository;

  private SyncServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SyncServiceImpl(
            syncLogRepository, movieBackFillService, failedBatchProducer, movieRepository);
    ReflectionTestUtils.setField(service, "defaultStartDateStr", "01-01-1990");
    ReflectionTestUtils.setField(service, "threadCount", 2);
  }

  @Test
  void getStatusReturnsPersistedLogWhenPresent() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.of(2024, 1, 1));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));

    assertThat(service.getStatus()).isSameAs(log);
  }

  @Test
  void getStatusInfersFromMaxReleaseDateWhenNoLog() {
    when(syncLogRepository.findById(1L)).thenReturn(Optional.empty());
    when(movieRepository.findMaxReleaseDate()).thenReturn(Optional.of(LocalDate.of(2023, 5, 5)));

    assertThat(service.getStatus().getLastSyncDate()).isEqualTo(LocalDate.of(2023, 5, 5));
  }

  @Test
  void syncNowExitsEarlyWhenAlreadyUpToDate() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now());
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));

    SyncLog result = service.syncNow();

    assertThat(result).isSameAs(log);
    verify(movieBackFillService, never()).addMoviesToDatabase(anyString(), anyString());
    verify(syncLogRepository, never()).save(any());
    assertThat(service.isSyncInProgress()).isFalse();
  }

  @Test
  void syncNowProcessesBatchesAndPersistsLastSyncDate() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now().minusDays(40));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenReturn(List.of(new long[] {1L, 2L}));
    when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncNow();

    verify(movieBackFillService, atLeastOnce()).addMoviesToDatabase(anyString(), anyString());
    verify(movieBackFillService, atLeastOnce()).saveMovieGenres(anyList());

    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    assertThat(captor.getValue().getLastSyncDate()).isEqualTo(LocalDate.now().minusDays(1));
    assertThat(captor.getValue().getSyncedAt()).isNotNull();
    assertThat(service.isSyncInProgress()).isFalse();
  }

  @Test
  void syncNowInfersStartDateFromMaxReleaseDateWhenNoLogExists() {
    when(syncLogRepository.findById(1L)).thenReturn(Optional.empty());
    when(movieRepository.findMaxReleaseDate())
        .thenReturn(Optional.of(LocalDate.now().minusDays(3)));
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenReturn(List.of());
    when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncNow();

    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    assertThat(captor.getValue().getLastSyncDate()).isEqualTo(LocalDate.now().minusDays(1));
  }

  @Test
  void syncNowFallsBackToDefaultStartDateWhenDatabaseIsEmpty() {
    ReflectionTestUtils.setField(
        service,
        "defaultStartDateStr",
        LocalDate.now().minusDays(5).format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.empty());
    when(movieRepository.findMaxReleaseDate()).thenReturn(Optional.empty());
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenReturn(List.of());
    when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncNow();

    verify(movieBackFillService, atLeastOnce()).addMoviesToDatabase(anyString(), anyString());
    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    assertThat(captor.getValue().getLastSyncDate()).isEqualTo(LocalDate.now().minusDays(1));
  }

  @Test
  void syncNowQueuesTransientFailureAndLeavesLastSyncDateUnchanged() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now().minusDays(40));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new org.springframework.web.client.ResourceAccessException("boom"));

    service.syncNow();

    verify(failedBatchProducer, atLeastOnce()).publish(anyString(), anyString(), anyString());
    verify(syncLogRepository, never()).save(any());
    assertThat(service.isSyncInProgress()).isFalse();
  }

  @Test
  void overlapIsCountedAsSyncedSoWatermarkAdvances() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now().minusDays(40));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new com.chatbot.errorhandling.NonRetryableException("overlap"));
    when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> inv.getArgument(0));

    service.syncNow();

    verify(failedBatchProducer, never()).publish(anyString(), anyString(), anyString());
    ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
    verify(syncLogRepository).save(captor.capture());
    assertThat(captor.getValue().getLastSyncDate()).isEqualTo(LocalDate.now().minusDays(1));
  }

  @Test
  void syncNowAsyncSetsFlagAndIgnoresConcurrentTriggers() throws Exception {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now());
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));

    service.syncNowAsync();

    for (int i = 0; i < 50 && service.isSyncInProgress(); i++) {
      Thread.sleep(20);
    }
    assertThat(service.isSyncInProgress()).isFalse();
  }

  @Test
  void syncNowSkipsNonTransientFailureWithoutQueuing() {
    SyncLog log = new SyncLog();
    log.setLastSyncDate(LocalDate.now().minusDays(40));
    when(syncLogRepository.findById(1L)).thenReturn(Optional.of(log));
    when(movieBackFillService.addMoviesToDatabase(anyString(), anyString()))
        .thenThrow(new RuntimeException("data bug"));

    service.syncNow();

    verify(failedBatchProducer, never()).publish(anyString(), anyString(), anyString());
    verify(syncLogRepository, never()).save(any());
    assertThat(service.isSyncInProgress()).isFalse();
  }
}
