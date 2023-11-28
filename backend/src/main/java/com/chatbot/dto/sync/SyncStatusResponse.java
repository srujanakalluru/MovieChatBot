package com.chatbot.dto.sync;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SyncStatusResponse(
    LocalDate lastSyncDate, LocalDateTime syncedAt, boolean syncInProgress) {}
