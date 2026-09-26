package com.huvo.identity.department.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.identity.config.RabbitConfig;
import com.huvo.identity.event.EventEnvelope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes department domain events on the identity exchange (Huvo_Backend_Context.md Section 7):
 * enveloped, at-least-once, key {@code department.<action>}.
 *
 * <p>Mirrors {@code EmployeeEventPublisher}: publishing happens after the database write and is not
 * transactional with it, so consumers must be able to re-fetch state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentEventPublisher {

  /** One place that names the service for the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "identity-service";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public void publishDepartmentCreated(Long departmentId) {
    publish("department.created", departmentId);
  }

  public void publishDepartmentUpdated(Long departmentId) {
    publish("department.updated", departmentId);
  }

  public void publishDepartmentDeleted(Long departmentId) {
    publish("department.deleted", departmentId);
  }

  private void publish(String eventType, Long departmentId) {
    EventEnvelope<Long> envelope = EventEnvelope.of(eventType, PRODUCED_BY, departmentId);
    try {
      rabbitTemplate.convertAndSend(
          RabbitConfig.IDENTITY_EXCHANGE, eventType, objectMapper.writeValueAsString(envelope));
      log.info("Published {} for department {}", eventType, departmentId);
    } catch (JsonProcessingException e) {
      // Never fail the API call over event serialisation; log loudly instead.
      log.error("Could not serialise {} envelope for department {}", eventType, departmentId, e);
    }
  }
}
