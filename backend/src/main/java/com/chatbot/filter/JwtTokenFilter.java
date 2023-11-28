package com.chatbot.filter;

import com.chatbot.utils.JwtTokenUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtTokenFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenUtil jwtTokenUtil;

  public JwtTokenFilter(JwtTokenUtil jwtTokenUtil) {
    this.jwtTokenUtil = jwtTokenUtil;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    getBearerToken(request.getHeader("Authorization")).ifPresent(token -> {
      try {
        String email = jwtTokenUtil.getClaimFromToken(token, c -> c.get("email", String.class));
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
            new UsernamePasswordAuthenticationToken(email, null, authoritiesFromToken(token));
        usernamePasswordAuthenticationToken.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
      } catch (JwtException | IllegalArgumentException e) {
        SecurityContextHolder.clearContext();
      }
    });

    filterChain.doFilter(request, response);
  }

  // Maps the token's "roles" claim to authorities. Tokens issued before roles existed
  // (or any without the claim) default to ROLE_USER so existing sessions keep working.
  private List<SimpleGrantedAuthority> authoritiesFromToken(String token) {
    List<?> roles = jwtTokenUtil.getClaimFromToken(token, c -> c.get("roles", List.class));
    if (roles == null || roles.isEmpty()) {
      return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    return roles.stream()
        .map(r -> new SimpleGrantedAuthority(String.valueOf(r)))
        .toList();
  }

  private Optional<String> getBearerToken(String headerVal) {
    if (headerVal != null && headerVal.startsWith(BEARER_PREFIX)) {
      return Optional.of(headerVal.substring(BEARER_PREFIX.length()).trim());
    }
    return Optional.empty();
  }
}
