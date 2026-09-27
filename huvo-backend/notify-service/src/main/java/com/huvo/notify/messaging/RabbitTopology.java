package com.huvo.notify.messaging;

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
 * <p>notify-service is the only service that consumes from every other one, so it declares a queue
 * on each exchange it reads. All four exchanges are declared here rather than only the bindings, so
 * this service can bind against a broker that has never seen identity-service, attendance-service
 * or worklife-service start. A consumer depending on a producer's startup order means a rolling
 * restart of any service takes notifications down with it.
 *
 * <p>One queue per source context, not one per event. Each queue then binds the precise keys it
 * needs, so an event nobody acts on is never even delivered:
 *
 * <ul>
 *   <li>{@code attendance.#} - both late and auto-absent are notified, so the wildcard is right
 *   <li>{@code task.#} - all three task events are notified
 *   <li>{@code leave.#} - applied, approved and rejected are all notified
 * </ul>
 *
 * <p>Bindings are the precise constants from {@code huvo-event-contracts} rather than string
 * literals typed here, so a renamed routing key fails the build instead of silently unbinding a
 * queue.
 */
@Configuration
public class RabbitTopology {

  /** This service's queue on the identity exchange. */
  public static final String IDENTITY_QUEUE = "notify.identity-consume";

  /** This service's queue on the attendance exchange. */
  public static final String ATTENDANCE_QUEUE = "notify.attendance-consume";

  /** This service's queue on the task exchange. */
  public static final String TASK_QUEUE = "notify.task-consume";

  /** This service's queue on the leave exchange. */
  public static final String LEAVE_QUEUE = "notify.leave-consume";

  @Bean
  public TopicExchange identityExchange() {
    return new TopicExchange(EventTypes.IDENTITY_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange attendanceExchange() {
    return new TopicExchange(EventTypes.ATTENDANCE_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange taskExchange() {
    return new TopicExchange(EventTypes.TASK_EXCHANGE, true, false);
  }

  @Bean
  public TopicExchange leaveExchange() {
    return new TopicExchange(EventTypes.LEAVE_EXCHANGE, true, false);
  }

  @Bean
  public Queue identityQueue() {
    return new Queue(IDENTITY_QUEUE, true);
  }

  @Bean
  public Queue attendanceQueue() {
    return new Queue(ATTENDANCE_QUEUE, true);
  }

  @Bean
  public Queue taskQueue() {
    return new Queue(TASK_QUEUE, true);
  }

  @Bean
  public Queue leaveQueue() {
    return new Queue(LEAVE_QUEUE, true);
  }

  @Bean
  public Binding attendanceBinding() {
    return BindingBuilder.bind(attendanceQueue())
        .to(attendanceExchange())
        .with(EventTypes.ATTENDANCE_ALL);
  }

  @Bean
  public Binding taskBinding() {
    return BindingBuilder.bind(taskQueue()).to(taskExchange()).with(EventTypes.TASK_ALL);
  }

  @Bean
  public Binding leaveBinding() {
    return BindingBuilder.bind(leaveQueue()).to(leaveExchange()).with(EventTypes.LEAVE_ALL);
  }
}
