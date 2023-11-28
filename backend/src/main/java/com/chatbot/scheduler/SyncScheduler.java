package com.chatbot.scheduler;

import com.chatbot.service.SyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SyncScheduler {

  private final SyncService syncService;

  @Autowired
  public SyncScheduler(SyncService syncService) {
    this.syncService = syncService;
  }

  @Async
  @EventListener(ApplicationReadyEvent.class)
  public void syncOnStartup() {
    try {
      log.info("Startup check - looking for pending data....");
      syncService.syncNow();
    } catch (Exception e) {
      log.warn("Startup sync failed: {}", e.getMessage());
    }
  }

  @Async
  @Scheduled(cron = "0 30 3 * * *")
  public void syncScheduled() {
    try {
      log.info("Scheduled daily sync starting....");
      syncService.syncNow();
    } catch (Exception e) {
      log.warn("Scheduled sync failed: {}", e.getMessage());
    }
  }
}
