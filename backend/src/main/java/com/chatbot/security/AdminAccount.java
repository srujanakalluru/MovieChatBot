package com.chatbot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The single non-SSO admin account. Username and password come from configuration
 * (security.admin.*, overridable via ADMIN_USERNAME / ADMIN_PASSWORD), and the password
 * is BCrypt-hashed once at startup so the plaintext is never held for comparison.
 */
@Component
public class AdminAccount {

  private final String username;
  private final String passwordHash;
  private final PasswordEncoder passwordEncoder;

  public AdminAccount(
      @Value("${security.admin.username:admin}") String username,
      @Value("${security.admin.password:admin}") String password,
      PasswordEncoder passwordEncoder) {
    this.username = username;
    this.passwordEncoder = passwordEncoder;
    this.passwordHash = passwordEncoder.encode(password);
  }

  public boolean matches(String candidateUsername, String candidatePassword) {
    return username.equals(candidateUsername)
        && passwordEncoder.matches(candidatePassword, passwordHash);
  }

  public String username() {
    return username;
  }
}
