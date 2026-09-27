package com.huvo.attendance.config;

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
 * <p>{@code lazy-initialization=false} is the whole point. Section 8.3 sets it to true for the
 * memory win, and under it {@code SecurityConfig} is never instantiated unless something asks for
 * the web layer - so the 90 tests in this service, none of which touch it, all passed while {@code
 * SecurityConfig} injected a {@code HuvoTokenService} that no configuration class defined. This
 * service would have crash-looped on the first {@code systemctl restart}.
 *
 * <p>Assertions are on the injected beans rather than merely "the context started": a test that
 * autowires nothing proves nothing, because the beans it was meant to check were never going to be
 * created.
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
    // The bean SecurityConfig constructor-injects. If this is null, nothing else in the class
    // matters - the service will not start.
    assertThat(tokens).isNotNull();
  }

  @Test
  void theSecurityFilterChainIsBuilt() {
    // Building this is what forces SecurityConfig to be created under lazy initialisation, which is
    // precisely the step the other 90 tests never take.
    assertThat(securityFilterChain).isNotNull();
  }
}
