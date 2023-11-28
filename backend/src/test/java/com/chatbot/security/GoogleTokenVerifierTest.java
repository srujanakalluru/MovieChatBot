package com.chatbot.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GoogleTokenVerifierTest {

  private final GoogleTokenVerifier verifier = new GoogleTokenVerifier("test-client-id");

  @Test
  void malformedTokenIsRejected() {
    assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void garbageThreePartTokenIsRejected() {
    assertThatThrownBy(() -> verifier.verify("aaa.bbb.ccc"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
