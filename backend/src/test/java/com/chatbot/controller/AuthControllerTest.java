package com.chatbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.chatbot.dto.auth.AuthResponse;
import com.chatbot.dto.auth.GoogleLoginRequest;
import com.chatbot.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @Mock private AuthService authService;
  @InjectMocks private AuthController controller;

  @Test
  void validTokenReturnsAuthResponse() {
    AuthResponse expected = new AuthResponse("jwt", "user@example.com", "Movie Fan", "pic");
    when(authService.loginWithGoogle("google-token")).thenReturn(expected);

    ResponseEntity<AuthResponse> response =
        controller.google(new GoogleLoginRequest("google-token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isSameAs(expected);
  }

  @Test
  void invalidTokenReturns401() {
    when(authService.loginWithGoogle("bad-token"))
        .thenThrow(new IllegalArgumentException("Invalid Google ID token"));

    ResponseEntity<AuthResponse> response =
        controller.google(new GoogleLoginRequest("bad-token"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNull();
  }
}
