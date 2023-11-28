package com.chatbot.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.utils.JwtTokenUtil;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtTokenFilterTest {

  private final JwtTokenUtil jwtTokenUtil =
      new JwtTokenUtil("test-secret-key-that-is-long-enough-32b!", 3_600_000);
  private final JwtTokenFilter filter = new JwtTokenFilter(jwtTokenUtil);

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private MockFilterChain chain;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    chain = new MockFilterChain();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void validTokenAuthenticatesUser() throws ServletException, IOException {
    String token = jwtTokenUtil.createToken("sub-123", "user@example.com", "Movie Fan");
    request.addHeader("Authorization", "Bearer " + token);

    filter.doFilterInternal(request, response, chain);

    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    assertThat(auth).isNotNull();
    assertThat(auth.getPrincipal()).isEqualTo("user@example.com");
    assertThat(auth.getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_USER");
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void invalidTokenLeavesContextEmptyAndContinuesChain() throws ServletException, IOException {
    request.addHeader("Authorization", "Bearer not-a-real-token");

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void missingHeaderLeavesContextEmpty() throws ServletException, IOException {
    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void nonBearerHeaderIsIgnored() throws ServletException, IOException {
    request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

    filter.doFilterInternal(request, response, chain);

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  }
}
