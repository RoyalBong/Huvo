package com.huvo.identity.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import com.huvo.security.HuvoTokenService;

/**
 * Section 9.1: that this context can actually start.
 *
 * <p>Written against a service that already had {@link TokenServiceConfig} and therefore already
 * had a {@code HuvoTokenService} bean, so this test is here to prove the <em>rest</em> of the graph
 * - and it immediately found one more missing bean, in {@code AuditRecorder}.
 *
 * <p>{@code lazy-initialization=false} is the point. Section 8.3 sets it true for the memory win,
 * and under it nothing that only the web layer asks for is ever created, so the ordinary tests here
 * pass against a context that could not start.
 *
 * <p>Assertions are on injected beans, not merely on "the context started": a test that autowires
 * nothing proves nothing, because the beans it was meant to check were never going to be created.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.main.lazy-initialization=false")
class ApplicationContextStartsTest {

  @Autowired private HuvoTokenService tokens;
  @Autowired private SecurityFilterChain securityFilterChain;

  @Test
  void theTokenServiceBeanIsDefined() {
    assertThat(tokens).isNotNull();
  }

  @Test
  void theSecurityFilterChainIsBuilt() {
    // Building this is what forces every configuration class the web layer depends on to be created
    // under lazy initialisation - which is precisely the step the other tests never take.
    assertThat(securityFilterChain).isNotNull();
  }
}
