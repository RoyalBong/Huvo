package com.huvo.notify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.notify.service.DynamoNotificationStore;
import com.huvo.notify.service.InMemoryNotificationStore;
import com.huvo.notify.service.NotificationStore;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * Chooses the notification feed implementation.
 *
 * <p>Both options in one class on purpose: "which store is live" should be answerable by reading
 * one file, and having the durable implementation and the local one selected by two scattered
 * annotations is how a service ends up silently running the in-memory one in an environment nobody
 * checked.
 *
 * <p>Credentials come from the default AWS provider chain - the instance role on EC2, the
 * developer's {@code AWS_PROFILE} locally - and no key is ever configured in code (Section 8.1).
 */
@Configuration
public class NotificationStoreConfig {

  /**
   * The durable feed, used whenever a table is named.
   *
   * <p>On-demand capacity, per Section 3.4: no throughput to tune at this scale, and a notification
   * burst at shift start should not need a provisioned increase first.
   */
  @Bean
  @ConditionalOnExpression("!'${huvo.notifications.table:}'.trim().isEmpty()")
  NotificationStore dynamoNotificationStore(
      @Value("${huvo.notifications.table}") String tableName,
      @Value("${huvo.notifications.region:ap-south-1}") String region) {
    return new DynamoNotificationStore(
        DynamoDbClient.builder().region(Region.of(region)).build(), tableName);
  }

  /**
   * The in-memory feed, used when no table is named. Local work and tests only.
   *
   * <p>SpEL rather than {@code @ConditionalOnProperty} on purpose. {@code havingValue = ""} does
   * not mean "the value is empty" - it means "the property is present and not {@code false}", so it
   * matches a configured table too, leaving the context with two {@code NotificationStore} beans
   * and failing to start. These two conditions are written as exact complements so exactly one
   * store exists in any configuration; NotificationStoreWithTableTest and
   * NotificationStoreWithoutTableTest exist to keep it that way.
   */
  @Bean
  @ConditionalOnExpression("'${huvo.notifications.table:}'.trim().isEmpty()")
  NotificationStore inMemoryNotificationStore() {
    return new InMemoryNotificationStore();
  }
}
