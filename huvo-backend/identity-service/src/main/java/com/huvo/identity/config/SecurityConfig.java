package com.huvo.identity.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.identity.exception.ErrorResponse;
import com.huvo.security.HuvoTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization for the bearer-token API (Huvo_Backend_Context.md Sections 4.1, 4.2). Tokens are
 * issued by the auth domain and validated in-process by {@link HuvoTokenService} - there is no auth
 * proxy, no OAuth2/OIDC provider and no session, so {@code STATELESS} plus CSRF off (nothing
 * authenticates via cookie).
 *
 * <p>This chain answers one question - "is this a valid, unexpired access token?" - and leaves the
 * role rules to {@code @PreAuthorize} on the controllers, because "who may do what" is a domain
 * decision that differs per endpoint (Section 4.1: one Access Role per user drives
 * {@code @PreAuthorize}). The {@code departmentIds} claim is re-checked inside the services.
 *
 * <p>Authentication and authorization failures are rendered as the documented error body (Section
 * 10) rather than Spring Security's empty 401/403, so clients parse one shape.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /** The only endpoints reachable without a valid access token. */
  private static final String[] PUBLIC_PATHS = {
    "/api/auth/login", "/api/auth/refresh", "/actuator/health", "/actuator/health/**"
  };

  private final HuvoTokenService tokens;
  private final ObjectMapper objectMapper;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            requests ->
                requests.requestMatchers(PUBLIC_PATHS).permitAll().anyRequest().authenticated())
        .exceptionHandling(
            handling ->
                handling
                    .authenticationEntryPoint(
                        (request, response, ex) ->
                            writeError(
                                request,
                                response,
                                HttpStatus.UNAUTHORIZED,
                                authenticationFailureReason(request)))
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            writeError(
                                request,
                                response,
                                HttpStatus.FORBIDDEN,
                                "Your role does not permit this operation")))
        .addFilterBefore(
            new JwtAuthenticationFilter(tokens), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  /** The one BCrypt encoder used for every stored password (Section 4.2, BCrypt hashing). */
  @Bean
  BCryptPasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  private void writeError(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper
        .writer()
        .writeValue(
            response.getOutputStream(),
            ErrorResponse.of(status.value(), status.name(), message, request.getRequestURI()));
  }

  private static String authenticationFailureReason(HttpServletRequest request) {
    Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_FAILURE_ATTRIBUTE);
    // A token that merely expired should be refreshed; anything else means re-login. The reason
    // text comes from huvo-security-lib and never contains the token itself.
    return reason != null ? reason.toString() : "A bearer token is required for this request";
  }
}
