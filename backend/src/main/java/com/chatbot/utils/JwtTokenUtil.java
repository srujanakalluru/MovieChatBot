package com.chatbot.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class JwtTokenUtil {

  private static final String DEV_DEFAULT_SECRET = "dev-only-secret-change-me-32-bytes-min!!";

  private final SecretKey signingKey;
  private final long validityInMilliseconds;

  @Autowired
  public JwtTokenUtil(
      @Value("${security.jwt.token.secret-key}") String secretKey,
      @Value("${security.jwt.token.expiration}") long milliseconds) {
    if (DEV_DEFAULT_SECRET.equals(secretKey)) {
      log.warn(
          "JWT signing key is the built-in development default - anyone who reads the "
              + "repository can forge tokens. Set JWT_SECRET before any non-local use.");
    }
    this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    this.validityInMilliseconds = milliseconds;
  }

  public String createToken(String googleSub, String email, String name) {
    return createToken(googleSub, email, name, List.of("ROLE_USER"));
  }

  public String createToken(String subject, String email, String name, List<String> roles) {
    Date now = new Date();
    return Jwts.builder()
        .subject(subject)
        .claim("email", email == null ? "" : email)
        .claim("name", name == null ? "" : name)
        .claim("roles", roles)
        .issuedAt(now)
        .expiration(new Date(now.getTime() + validityInMilliseconds))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
    Claims claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
    return claimsResolver.apply(claims);
  }
}
