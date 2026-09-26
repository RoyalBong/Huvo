package com.huvo.attendance.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.events.EventTypes;

/**
 * This service's messaging topology (Huvo_Backend_Context.md Section 7).
 *
 * <p>Two exchanges, because this service both consumes and publishes: {@code attendance.exchange}
 * is the one it publishes on, and it declares its own queue on {@code identity.exchange} for the
 * login stream. Publishers never know who listens, and consumers never declare an exchange that is
 * not theirs.
 *
 * <p>Bindings use the wildcard patterns from {@code huvo-event-contracts}, so a new attendance
 * event reaches notify-service without anyone editing this class.
 */
@Configuration
public class RabbitTopology {

  /** This service publishes lateness outcomes here. */
  public static final String ATTENDANCE_EXCHANGE = "attendance.exchange";

  /** This service's private queue on the identity exchange. */
  public static final String LOGIN_QUEUE = "attendance.login-consume";

  @Bean
  public TopicExchange attendanceExchange() {
    // durable (survives a broker restart), non-auto-delete (declared topology is stable)
    return new TopicExchange(ATTENDANCE_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange identityExchange() {
    // Declared, not published to: the exchange is identity-service's, and declaring it here means
    // this service can bind its queue on a broker that has not seen identity-service start yet.
    return new TopicExchange(EventTypes.IDENTITY_EXCHANGE, true, false);
  }

  @Bean
  public Queue loginConsumeQueue() {
    return new Queue(LOGIN_QUEUE, true);
  }

  @Bean
  public Binding loginConsumeBinding() {
    return BindingBuilder.bind(loginConsumeQueue())
        .to(identityExchange())
        .with(EventTypes.USER_LOGIN_SUCCESS);
  }
}
