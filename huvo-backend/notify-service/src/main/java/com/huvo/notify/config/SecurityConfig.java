package com.huvo.notify.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.notify.error.ErrorResponse;
import com.huvo.notify.messaging.JwtAuthenticationFilter;
import com.huvo.security.HuvoTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Authorization for the notify API (Sections 4.1, 4.2).
 *
 * <p>Stateless, CSRF off, no form login and no OAuth2/OIDC provider: identity-service issues the
 * tokens and there is no auth proxy, so every service validates the JWT in-process. Same shape as
 * the other services' chains, repeated deliberately - there is no shared web module (Section 11).
 *
 * <p>{@code /ws/notify} is deliberately absent from the permitted list. It is not governed by this
 * chain at all: a WebSocket upgrade bypasses the servlet filter chain entirely, and is
 * authenticated by {@code WebSocketConfig}'s handshake interceptor instead. Listing it here would
 * be a comment that looks like a control and is not one.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  /** The only endpoints reachable without a valid access token. */
  private static final String[] PUBLIC_PATHS = {"/actuator/health", "/actuator/health/**"};

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
                                "A valid access token is required for this endpoint"))
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

  private void writeError(
      HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper
        .writer()
        .writeValue(
            response.getOutputStream(),
            ErrorResponse.of(
                status.value(), status.name(), message, request.getRequestURI(), null));
  }
}
