package com.chatbot.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtTokenUtilTest {

  private static final String SECRET = "test-secret-key-that-is-long-enough-32b!";

  private final JwtTokenUtil util = new JwtTokenUtil(SECRET, 3_600_000);

  @Test
  void roundTripPreservesClaims() {
    String token = util.createToken("sub-123", "user@example.com", "Movie Fan");

    String subject = util.getClaimFromToken(token, Claims::getSubject);
    String email = util.getClaimFromToken(token, c -> c.get("email", String.class));
    String name = util.getClaimFromToken(token, c -> c.get("name", String.class));

    assertThat(subject).isEqualTo("sub-123");
    assertThat(email).isEqualTo("user@example.com");
    assertThat(name).isEqualTo("Movie Fan");
  }

  @Test
  void nullEmailAndNameBecomeEmptyClaims() {
    String token = util.createToken("sub-123", null, null);

    String email = util.getClaimFromToken(token, c -> c.get("email", String.class));
    String name = util.getClaimFromToken(token, c -> c.get("name", String.class));

    assertThat(email).isEmpty();
    assertThat(name).isEmpty();
  }

  @Test
  void expiredTokenIsRejected() {
    JwtTokenUtil shortLived = new JwtTokenUtil(SECRET, -1_000);
    String token = shortLived.createToken("sub-123", "user@example.com", "Movie Fan");

    assertThatThrownBy(() -> util.getClaimFromToken(token, Claims::getSubject))
        .isInstanceOf(ExpiredJwtException.class);
  }

  @Test
  void tokenSignedWithDifferentKeyIsRejected() {
    JwtTokenUtil other = new JwtTokenUtil("another-secret-key-that-is-long-enough!!", 3_600_000);
    String token = other.createToken("sub-123", "user@example.com", "Movie Fan");

    assertThatThrownBy(() -> util.getClaimFromToken(token, Claims::getSubject))
        .isInstanceOf(JwtException.class);
  }

  @Test
  void tamperedTokenIsRejected() {
    String token = util.createToken("sub-123", "user@example.com", "Movie Fan");
    String tampered = token.substring(0, token.length() - 4) + "abcd";

    assertThatThrownBy(() -> util.getClaimFromToken(tampered, Claims::getSubject))
        .isInstanceOf(JwtException.class);
  }
}
