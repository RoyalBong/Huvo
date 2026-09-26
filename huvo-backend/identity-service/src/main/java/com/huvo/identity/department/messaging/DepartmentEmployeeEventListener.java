package com.huvo.identity.department.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.identity.config.RabbitConfig;
import com.huvo.identity.event.EventEnvelope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reacts to employee events for department membership bookkeeping (Huvo_Backend_Context.md Section
 * 7). Owns its own queue binding ({@value RabbitConfig#DEPARTMENT_EMPLOYEE_SYNC_QUEUE} on {@value
 * RabbitConfig#IDENTITY_EXCHANGE}, key {@code employee.#}); the publisher does not know this
 * listener exists.
 *
 * <p>Delivery is at-least-once: dedupe on {@code envelope.eventId()} before any side effect lands.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepartmentEmployeeEventListener {

  private final ObjectMapper objectMapper;

  @RabbitListener(queues = RabbitConfig.DEPARTMENT_EMPLOYEE_SYNC_QUEUE)
  public void onEmployeeEvent(String payload) {
    try {
      EventEnvelope<JsonNode> envelope = objectMapper.readValue(payload, EventEnvelope.class);
      log.info(
          "Received {} from {} (eventId {})",
          envelope.eventType(),
          envelope.producedBy(),
          envelope.eventId());
      // TODO(phase-1): apply department membership changes once the org-chart
      // model (Section 4.2) exists; dedupe on envelope.eventId() first.
    } catch (Exception e) {
      // Log and drop: requeueing a malformed event would poison-loop the queue.
      log.error("Dropping unparseable employee event: {}", payload, e);
    }
  }
}
