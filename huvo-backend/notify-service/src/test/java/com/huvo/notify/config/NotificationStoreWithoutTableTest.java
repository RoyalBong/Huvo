package com.huvo.notify.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.huvo.notify.service.InMemoryNotificationStore;
import com.huvo.notify.service.NotificationStore;

/**
 * That a {@link NotificationStore} bean exists, and is the local one, with no table configured.
 *
 * <p>Worth its own test. The two implementations are selected by a {@code @ConditionalOn...} pair,
 * and a mistake in either condition does not fail the build - it produces a context with no {@code
 * NotificationStore} bean, which surfaces as a startup failure on the EC2 instance rather than in
 * CI.
 *
 * <p>Separate classes rather than one with nested static cases, because Surefire does not run
 * nested classes as test classes, and a test that silently never executes is worse than no test.
 */
@ActiveProfiles("test")
@SpringBootTest
class NotificationStoreWithoutTableTest {

  @Autowired private NotificationStore store;

  @Test
  void theInMemoryStoreIsSelectedWhenNoTableIsConfigured() {
    assertThat(store).isInstanceOf(InMemoryNotificationStore.class);
  }
}
