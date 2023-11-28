package com.chatbot.service;

import com.chatbot.beans.SyncLog;

public interface SyncService {

  SyncLog getStatus();

  SyncLog syncNow();

  void syncNowAsync();

  boolean isSyncInProgress();
}
