package com.huvo.worklife.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes this service's events (Huvo_Backend_Context.md Section 7).
 *
 * <p>Every payload is a shared {@code huvo-event-contracts} type rather than something built here,
 * so a producer and a consumer cannot quietly disagree about a field - they are the same class.
 *
 * <p>Publishes are not wrapped in a transaction with the database write. That is a deliberate
 * trade-off rather than an oversight: wrapping them would need a distributed transaction across
 * MySQL and RabbitMQ to be atomic, and the failure modes of the outbox pattern (a poll that
 * duplicates) are cheaper than the failure modes of losing a commit. Consumers are therefore
 * expected to be idempotent, which attendance-service's leave listener already is.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorklifeEventPublisher {

  /** One place that names this service in the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "worklife-service";

  private final RabbitTemplate rabbit;
  private final ObjectMapper objectMapper;

  /**
   * Tells the approvers a request is waiting (Section 7).
   *
   * @param payload the applied leave
   */
  public void publishLeaveApplied(EventTypes.LeaveAppliedPayload payload) {
    publish(EventTypes.LEAVE_EXCHANGE, EventTypes.LEAVE_APPLIED, payload);
  }

  /**
   * Tells attendance-service and notify-service that leave was approved (Section 7).
   *
   * <p>attendance-service binds the precise key {@code leave.approved}, not {@code leave.#}, and
   * marks every day in the range {@code ON_LEAVE}. Both date fields are inclusive.
   *
   * @param payload the approved leave
   */
  public void publishLeaveApproved(EventTypes.LeaveApprovedPayload payload) {
    publish(EventTypes.LEAVE_EXCHANGE, EventTypes.LEAVE_APPROVED, payload);
  }

  /**
   * Tells notify-service that leave was rejected (Section 7).
   *
   * @param payload the rejected leave
   */
  public void publishLeaveRejected(EventTypes.LeaveRejectedPayload payload) {
    publish(EventTypes.LEAVE_EXCHANGE, EventTypes.LEAVE_REJECTED, payload);
  }

  /**
   * Tells notify-service a task was assigned (Section 6.2).
   *
   * @param payload the assigned task
   */
  public void publishTaskAssigned(EventTypes.TaskAssignedPayload payload) {
    publish(EventTypes.TASK_EXCHANGE, EventTypes.TASK_ASSIGNED, payload);
  }

  /**
   * Tells notify-service a task went past its deadline (Section 6.2).
   *
   * @param payload the overdue task
   */
  public void publishTaskOverdue(EventTypes.TaskOverduePayload payload) {
    publish(EventTypes.TASK_EXCHANGE, EventTypes.TASK_OVERDUE, payload);
  }

  /**
   * Tells notify-service a task was submitted late (Section 6.1).
   *
   * @param payload the late submission
   */
  public void publishTaskSubmittedLate(EventTypes.TaskSubmittedLatePayload payload) {
    publish(EventTypes.TASK_EXCHANGE, EventTypes.TASK_SUBMITTED_LATE, payload);
  }

  private void publish(String exchange, String routingKey, Object payload) {
    try {
      // Wrapped in the shared EventEnvelope, not sent bare. attendance-service's
      // LeaveApprovedListener
      // reads envelope.get("payload"), so a bare payload is silently dropped as "carried no
      // payload"
      // and no employee is ever marked ON_LEAVE. Producer and consumer are in different services,
      // so
      // this is the only place that shape is pinned - hence the shared type rather than a local
      // map.
      String body =
          objectMapper.writeValueAsString(EventEnvelope.of(routingKey, PRODUCED_BY, payload));
      rabbit.convertAndSend(exchange, routingKey, body);
    } catch (Exception e) {
      // A failed publish must be visible: the database write has already committed, so this is a
      // silent gap between the two, and swallowing it would make attendance never mark the leave.
      log.error("Failed to publish {} to {}", routingKey, exchange, e);
      throw new EventPublishException(routingKey, exchange, e);
    }
  }

  /**
   * A publish that did not reach the broker, wrapped so callers can fail the business operation.
   */
  public static class EventPublishException extends RuntimeException {

    public EventPublishException(String routingKey, String exchange, Throwable cause) {
      super("Failed to publish " + routingKey + " to " + exchange, cause);
    }
  }
}
