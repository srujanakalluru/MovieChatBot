package com.chatbot.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "{validation.username.required}") String username,
    @NotBlank(message = "{validation.password.required}") String password) {

  /** Masks the password so credentials never leak into logs. */
  @Override
  public String toString() {
    return "LoginRequest[username=" + username + ", password=***]";
  }
}
