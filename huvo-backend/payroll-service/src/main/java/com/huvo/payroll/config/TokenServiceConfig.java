package com.huvo.payroll.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.security.HuvoTokenService;

/**
 * The JWT validator for this service (Huo_Backend_Context.md Section 4.2).
 *
 * <p>Declared in its own configuration class rather than inside {@code SecurityConfig}, because
 * {@code SecurityConfig} injects this token service: a {@code @Bean} method in a configuration
 * class that also constructor-injects the same type makes the bean depend on itself, which Spring
 * refuses to create. Keeping the factory separate lets {@code SecurityConfig} and the
 * authentication filter share one singleton without that cycle.
 *
 * <p>Section 4.3 requires the key to come from Parameter Store on EC2, never from a committed file.
 * The value has no default, so a service with no key configured fails at startup rather than
 * accepting tokens it cannot verify.
 */
@Configuration
public class TokenServiceConfig {

  @Bean
  HuvoTokenService huvoTokenService(@Value("${huvo.jwt.signing-key}") String signingKey) {
    return new HuvoTokenService(signingKey);
  }
}
