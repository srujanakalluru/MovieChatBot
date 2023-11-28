package com.chatbot.beans;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "sync_log")
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class SyncLog {

  @Id
  private Long id = 1L;

  private LocalDate lastSyncDate;
  private LocalDateTime syncedAt;
}
