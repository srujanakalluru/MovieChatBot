package com.chatbot.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "app_user")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "google_sub", nullable = false, unique = true)
  private String googleSub;

  @Column(nullable = false)
  private String email;

  private String name;

  @Column(name = "picture_url")
  private String pictureUrl;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "last_login_at", nullable = false)
  private LocalDateTime lastLoginAt;
}
