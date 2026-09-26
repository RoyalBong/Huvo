package com.huvo.identity.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.security.HuvoTokenService;

/**
 * The single JWT issuer/validator for this service (Huvo_Backend_Context.md Section 4.2).
 *
 * <p>Declared in its own configuration class rather than inside {@code SecurityConfig}, because
 * {@code SecurityConfig} injects this token service: a {@code @Bean} method that the enclosing
 * configuration class also constructor-injects makes the bean depend on itself, which Spring fails
 * to create. Keeping the factory here lets {@code SecurityConfig}, {@code AuthService} and the
 * filter chain all take the same singleton without that cycle.
 *
 * <p>Fails fast on a short key, and Section 4.3 requires the value to come from Parameter Store -
 * never from a committed file.
 */
@Configuration
public class TokenServiceConfig {

  @Bean
  HuvoTokenService huvoTokenService(@Value("${huvo.jwt.signing-key}") String signingKey) {
    return new HuvoTokenService(signingKey);
  }
}
