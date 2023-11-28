package com.chatbot.service.impl;

import com.chatbot.beans.User;
import com.chatbot.dto.auth.AuthResponse;
import com.chatbot.repository.UserRepository;
import com.chatbot.security.AdminAccount;
import com.chatbot.security.GoogleTokenVerifier;
import com.chatbot.service.AuthService;
import com.chatbot.utils.JwtTokenUtil;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final GoogleTokenVerifier googleTokenVerifier;
  private final JwtTokenUtil jwtTokenUtil;
  private final UserRepository userRepository;
  private final AdminAccount adminAccount;

  @Override
  @Transactional
  public AuthResponse loginWithGoogle(String googleIdToken) {
    GoogleIdToken.Payload payload = googleTokenVerifier.verify(googleIdToken);

    String sub = payload.getSubject();
    String email = payload.getEmail();
    String name = (String) payload.get("name");
    String picture = (String) payload.get("picture");

    User user = userRepository.findByGoogleSub(sub)
        .map(existing -> {
          existing.setEmail(email);
          existing.setName(name);
          existing.setPictureUrl(picture);
          existing.setLastLoginAt(LocalDateTime.now());
          return existing;
        })
        .orElseGet(() -> User.builder()
            .googleSub(sub)
            .email(email)
            .name(name)
            .pictureUrl(picture)
            .createdAt(LocalDateTime.now())
            .lastLoginAt(LocalDateTime.now())
            .build());
    userRepository.save(user);

    log.info("Google SSO login: {}", email);
    return new AuthResponse(jwtTokenUtil.createToken(sub, email, name), email, name, picture);
  }

  @Override
  public AuthResponse loginWithCredentials(String username, String password) {
    if (!adminAccount.matches(username, password)) {
      throw new BadCredentialsException("Invalid username or password");
    }
    String name = adminAccount.username();
    String token = jwtTokenUtil.createToken(name, null, name, List.of("ROLE_ADMIN", "ROLE_USER"));
    log.info("Admin login: {}", name);
    return new AuthResponse(token, null, name, null);
  }
}
