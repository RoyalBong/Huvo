package com.huvo.identity.employee.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;
import com.huvo.identity.config.RabbitConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes employee domain events on the identity exchange (Huvo_Backend_Context.md Section 7):
 * enveloped, at-least-once, key {@code employee.<entity>.<action>}.
 *
 * <p>Publishing happens after the database write and is not transactional with it - a crash between
 * the two can lose an event, so consumers must be able to re-fetch state (Section 7 states this
 * contract explicitly).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeEventPublisher {

  /** One place that names the service for the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "identity-service";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public void publishEmployeeCreated(Long employeeId) {
    publish(EventTypes.EMPLOYEE_CREATED, employeeId);
  }

  public void publishEmployeeUpdated(Long employeeId) {
    publish(EventTypes.EMPLOYEE_UPDATED, employeeId);
  }

  public void publishEmployeeDeleted(Long employeeId) {
    publish(EventTypes.EMPLOYEE_DELETED, employeeId);
  }

  private void publish(String eventType, Long employeeId) {
    EventEnvelope<Long> envelope = EventEnvelope.of(eventType, PRODUCED_BY, employeeId);
    try {
      rabbitTemplate.convertAndSend(
          RabbitConfig.IDENTITY_EXCHANGE, eventType, objectMapper.writeValueAsString(envelope));
      log.info("Published {} for employee {}", eventType, employeeId);
    } catch (JsonProcessingException e) {
      // Never fail the API call over event serialisation; log loudly instead.
      log.error("Could not serialise {} envelope for employee {}", eventType, employeeId, e);
    }
  }
}
