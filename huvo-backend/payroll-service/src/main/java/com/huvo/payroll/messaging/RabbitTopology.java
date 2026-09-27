package com.huvo.payroll.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.events.EventTypes;

/**
 * This service's messaging topology (Huo_Backend_Context.md Section 7).
 *
 * <p>Two exchanges: {@code payroll.exchange} is published on, and the attendance exchange is
 * declared so a queue can be bound to it. Declaring the exchange that is not ours is what stops a
 * rolling restart of attendance-service from taking payroll's consumer down with it, and stops
 * payroll from depending on a producer's startup order.
 *
 * <p>The attendance binding is the precise key rather than {@code attendance.#}, so payroll is not
 * woken for lateness events it does not consume.
 */
@Configuration
public class RabbitTopology {

  @Bean
  public TopicExchange payrollExchange() {
    return new TopicExchange(EventTypes.PAYROLL_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange attendanceExchange() {
    return new TopicExchange(EventTypes.ATTENDANCE_EXCHANGE, true, false);
  }

  @Bean
  public Queue autoAbsentQueue() {
    return new Queue(AutoAbsentListener.QUEUE, true);
  }

  @Bean
  public Binding autoAbsentBinding() {
    return BindingBuilder.bind(autoAbsentQueue())
        .to(attendanceExchange())
        .with(EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED);
  }
}
