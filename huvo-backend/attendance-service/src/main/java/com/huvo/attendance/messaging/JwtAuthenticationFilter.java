package com.huvo.attendance.messaging;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.huvo.security.HuvoPrincipal;
import com.huvo.security.HuvoSecurityException;
import com.huvo.security.HuvoTokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates the bearer token in-process with {@code huvo-security-lib} and puts the caller in the
 * {@link SecurityContextHolder} as a {@link HuvoPrincipal} with one {@code ROLE_<role>} authority
 * (Section 4.2: no gateway validates tokens, so every service repeats this).
 *
 * <p>Deliberately not a Spring bean: Boot would auto-register any {@code Filter} bean for the whole
 * servlet context and it would then run twice. {@code SecurityConfig} constructs it once for the
 * chain instead.
 *
 * <p>A rejected token never writes the response here - the context is left empty and the configured
 * entry point renders the documented error body with the right status.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final HuvoTokenService tokens;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = bearerToken(request);
    if (token != null) {
      try {
        HuvoPrincipal principal = tokens.validateAccessToken(token);
        var authentication =
            new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (HuvoSecurityException e) {
        // The token is never logged, only the reason.
        log.debug("Rejected bearer token on {}: {}", request.getRequestURI(), e.getMessage());
      }
    }
    filterChain.doFilter(request, response);
  }

  /** The bare token, or null when there is no usable {@code Authorization: Bearer} header. */
  static String bearerToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      return null;
    }
    String value = header.substring(BEARER_PREFIX.length()).trim();
    return value.isEmpty() ? null : value;
  }
}
