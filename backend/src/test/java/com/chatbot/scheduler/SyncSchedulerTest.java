package com.chatbot.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.service.SyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerTest {

  @Mock private SyncService syncService;
  @InjectMocks private SyncScheduler scheduler;

  @Test
  void startupTriggersSync() {
    scheduler.syncOnStartup();
    verify(syncService).syncNow();
  }

  @Test
  void startupSwallowsSyncFailure() {
    when(syncService.syncNow()).thenThrow(new RuntimeException("sync failed"));
    assertThatCode(() -> scheduler.syncOnStartup()).doesNotThrowAnyException();
  }

  @Test
  void scheduledRunTriggersSync() {
    scheduler.syncScheduled();
    verify(syncService).syncNow();
  }
}
