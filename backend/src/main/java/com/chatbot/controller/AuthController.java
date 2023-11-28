package com.chatbot.controller;

import com.chatbot.dto.auth.AuthResponse;
import com.chatbot.dto.auth.GoogleLoginRequest;
import com.chatbot.dto.auth.LoginRequest;
import com.chatbot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Google SSO and admin login")
public class AuthController {

  private final AuthService authService;

  @Operation(summary = "Exchange a Google ID token for an application JWT")
  @PostMapping("/google")
  public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleLoginRequest request) {
    try {
      return ResponseEntity.ok(authService.loginWithGoogle(request.idToken()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }

  @Operation(summary = "Username/password login for the admin account")
  @PostMapping("/login")
  public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
    try {
      return ResponseEntity.ok(
          authService.loginWithCredentials(request.username(), request.password()));
    } catch (BadCredentialsException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
  }
}
