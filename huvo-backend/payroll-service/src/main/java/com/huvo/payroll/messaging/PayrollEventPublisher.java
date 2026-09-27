package com.huvo.payroll.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes this service's events (Huo_Backend_Context.md Section 7).
 *
 * <p>Payloads are shared {@code huvo-event-contracts} types rather than anything built here, so a
 * producer and a consumer cannot quietly disagree about a field - they are the same class.
 *
 * <p>Publishes are not inside the database transaction, deliberately. Making them atomic would need
 * a distributed transaction across MySQL and RabbitMQ; the cheaper failure to have is a run that
 * committed but whose event was lost, and a monthly run is re-runnable by a human far more easily
 * than a transaction coordinator can be debugged at 2am.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollEventPublisher {

  /** One place that names this service in the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "payroll-service";

  private final RabbitTemplate rabbit;
  private final ObjectMapper objectMapper;

  /**
   * Publishes {@code payroll.generated} once a run has produced its payslips.
   *
   * @param payload the completed run
   */
  public void publishPayrollGenerated(EventTypes.PayrollGeneratedPayload payload) {
    publish(EventTypes.PAYROLL_EXCHANGE, EventTypes.PAYROLL_GENERATED, payload);
  }

  private void publish(String exchange, String routingKey, Object payload) {
    try {
      // Wrapped in the shared EventEnvelope, not sent bare. Section 7.1: this exact mistake - a
      // bare
      // payload where an envelope was expected - has already been made once in this codebase, and
      // it
      // compiled cleanly and passed every test in its own service.
      String body =
          objectMapper.writeValueAsString(EventEnvelope.of(routingKey, PRODUCED_BY, payload));
      rabbit.convertAndSend(exchange, routingKey, body);
    } catch (Exception e) {
      // The payslips have already committed, so this is a silent gap between the two. Loud, because
      // "HR was told payroll ran" is exactly the thing that must not be quietly untrue.
      log.error("Failed to publish {} to {}", routingKey, exchange, e);
      throw new EventPublishException(routingKey, exchange, e);
    }
  }

  /** A publish that did not reach the broker. */
  public static class EventPublishException extends RuntimeException {

    public EventPublishException(String routingKey, String exchange, Throwable cause) {
      super("Failed to publish " + routingKey + " to " + exchange, cause);
    }
  }
}
