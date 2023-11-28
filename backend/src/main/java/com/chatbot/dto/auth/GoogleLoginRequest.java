package com.chatbot.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(@NotBlank(message = "{validation.idToken.required}") String idToken) {

  @Override
  public String toString() {
    return "GoogleLoginRequest[idToken=***]";
  }
}
