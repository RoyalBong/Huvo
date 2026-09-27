package com.huvo.payroll.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.huvo.security.HuvoTokenService;

/**
 * Section 9.1: that this context can actually start.
 *
 * <p>Written from the first commit of this service rather than added after a missing bean was found
 * in three others. {@code lazy-initialization=false} is the point - Section 8.3 sets it true for
 * the memory win, and under it {@code SecurityConfig} is never created unless something asks for
 * the web layer, so every ordinary test here would pass with a completely broken startup.
 *
 * <p>Assertions are on injected beans, not merely on "the context started": a test that autowires
 * nothing proves nothing, because the beans it was meant to check were never going to be created.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.main.lazy-initialization=false")
@TestPropertySource(
    properties = "huvo.jwt.signing-key=test-only-signing-key-never-used-outside-the-test-profile")
class ApplicationContextStartsTest {

  @Autowired private HuvoTokenService tokens;
  @Autowired private SecurityFilterChain securityFilterChain;

  @Test
  void theTokenServiceBeanIsDefined() {
    // The bean SecurityConfig constructor-injects. If this is null, the service will not start.
    assertThat(tokens).isNotNull();
  }

  @Test
  void theSecurityFilterChainIsBuilt() {
    // Building this is what forces SecurityConfig to be created under lazy initialisation.
    assertThat(securityFilterChain).isNotNull();
  }
}
