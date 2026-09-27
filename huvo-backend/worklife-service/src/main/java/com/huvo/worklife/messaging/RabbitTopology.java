package com.huvo.worklife.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.events.EventTypes;

/**
 * This service's messaging topology (Huvo_Backend_Context.md Section 7).
 *
 * <p>worklife-service only publishes, so it declares no queues. One exchange per bounded context:
 * {@code task.exchange} and {@code leave.exchange}, taken from {@code huvo-event-contracts} so the
 * names cannot drift from the constants attendance-service binds against.
 *
 * <p>Declaring the exchange it publishes on is what lets it start before notify-service or
 * attendance-service does. A rolling restart of a consumer must not take the producer down with it,
 * and it must not fail either.
 */
@Configuration
public class RabbitTopology {

  @Bean
  public TopicExchange leaveExchange() {
    // durable (survives a broker restart), non-auto-delete (the declared topology is stable).
    return new TopicExchange(EventTypes.LEAVE_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange taskExchange() {
    return new TopicExchange(EventTypes.TASK_EXCHANGE, true, false);
  }
}
