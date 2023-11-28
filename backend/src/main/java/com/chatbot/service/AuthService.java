package com.chatbot.service;

import com.chatbot.dto.auth.AuthResponse;

public interface AuthService {

  AuthResponse loginWithGoogle(String googleIdToken);

  AuthResponse loginWithCredentials(String username, String password);
}
