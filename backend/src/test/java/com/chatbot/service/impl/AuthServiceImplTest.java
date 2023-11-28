package com.chatbot.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.beans.User;
import com.chatbot.dto.auth.AuthResponse;
import com.chatbot.repository.UserRepository;
import com.chatbot.security.GoogleTokenVerifier;
import com.chatbot.utils.JwtTokenUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

  @Mock private GoogleTokenVerifier googleTokenVerifier;
  @Mock private JwtTokenUtil jwtTokenUtil;
  @Mock private UserRepository userRepository;
  @InjectMocks private AuthServiceImpl authService;

  private GoogleIdToken.Payload payload;

  @BeforeEach
  void setUp() {
    payload = new GoogleIdToken.Payload();
    payload.setSubject("sub-123");
    payload.setEmail("user@example.com");
    payload.set("name", "Movie Fan");
    payload.set("picture", "http://pic");

    org.mockito.Mockito.lenient()
        .when(googleTokenVerifier.verify("google-token"))
        .thenReturn(payload);
    org.mockito.Mockito.lenient()
        .when(jwtTokenUtil.createToken("sub-123", "user@example.com", "Movie Fan"))
        .thenReturn("app-jwt");
  }

  @Test
  void firstLoginCreatesUser() {
    when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.empty());

    AuthResponse response = authService.loginWithGoogle("google-token");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getGoogleSub()).isEqualTo("sub-123");
    assertThat(saved.getEmail()).isEqualTo("user@example.com");
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getLastLoginAt()).isNotNull();

    assertThat(response.token()).isEqualTo("app-jwt");
    assertThat(response.email()).isEqualTo("user@example.com");
    assertThat(response.name()).isEqualTo("Movie Fan");
    assertThat(response.pictureUrl()).isEqualTo("http://pic");
  }

  @Test
  void repeatLoginUpdatesExistingUser() {
    LocalDateTime created = LocalDateTime.now().minusDays(30);
    User existing = User.builder()
        .id(7L)
        .googleSub("sub-123")
        .email("old@example.com")
        .name("Old Name")
        .createdAt(created)
        .lastLoginAt(created)
        .build();
    when(userRepository.findByGoogleSub("sub-123")).thenReturn(Optional.of(existing));

    authService.loginWithGoogle("google-token");

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getId()).isEqualTo(7L);
    assertThat(saved.getEmail()).isEqualTo("user@example.com");
    assertThat(saved.getName()).isEqualTo("Movie Fan");
    assertThat(saved.getCreatedAt()).isEqualTo(created);
    assertThat(saved.getLastLoginAt()).isAfter(created);
  }

  @Test
  void verifierFailurePropagatesWithoutSaving() {
    when(googleTokenVerifier.verify("google-token"))
        .thenThrow(new IllegalArgumentException("bad token"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> authService.loginWithGoogle("google-token"))
        .isInstanceOf(IllegalArgumentException.class);
    verify(userRepository, org.mockito.Mockito.never()).save(any());
  }
}
