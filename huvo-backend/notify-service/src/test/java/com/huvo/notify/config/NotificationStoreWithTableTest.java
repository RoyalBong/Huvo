package com.huvo.notify.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.huvo.notify.service.DynamoNotificationStore;
import com.huvo.notify.service.NotificationStore;

/**
 * That a configured table selects the durable store.
 *
 * <p>The client is built but never called, so the suite needs no AWS credentials - which is the
 * point. This verifies the selection logic, not the AWS round trip; a developer with {@code
 * AWS_PROFILE} set against the real {@code huvo-dev-notifications} table gets the actual thing.
 *
 * <p>The other half of the pair is {@code NotificationStoreWithoutTableTest}. Both are needed: a
 * condition that always matches would give a durable store to a test agent with no credentials, and
 * one that never matches would silently leave every deployment on the in-memory feed.
 */
@ActiveProfiles("test")
@SpringBootTest
@TestPropertySource(properties = "huvo.notifications.table=huvo-dev-notifications")
class NotificationStoreWithTableTest {

  @Autowired private NotificationStore store;

  @Test
  void theDynamoStoreIsSelectedWhenATableIsConfigured() {
    assertThat(store).isInstanceOf(DynamoNotificationStore.class);
  }
}
