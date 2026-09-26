package com.huvo.attendance.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.huvo.attendance.engine.LatenessDecision;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes what the lateness engine decided (Huvo_Backend_Context.md Sections 5.2, 7).
 *
 * <p>Two separate events because notify-service treats them differently: a late warning goes out
 * immediately per §5.2 step 5b, while an auto-absent notification is a summary to the employee and
 * their manager. Both carry ids and measurements, never a copy of the attendance row.
 *
 * <p>Publishing never fails the caller's operation. The decision is already committed by the time
 * we get here, so refusing to send a notification must not undo attendance state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceEventPublisher {

  /** One place that names the service for the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "attendance-service";

  private final RabbitTemplate rabbitTemplate;
  private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

  /**
   * Publishes {@code attendance.late.detected} when the engine marked the day late (step 5b).
   *
   * <p>Also fires for an auto-absent day, because that day was also late - the late-warning email
   * is still the right thing to send, and notify-service gets the escalation as a second event.
   */
  public void publishLateDetected(LatenessDecision decision) {
    if (!decision.isLate() || decision.lateMinutes() == null) {
      return;
    }
    publish(
        EventTypes.ATTENDANCE_LATE_DETECTED,
        decision.employeeId(),
        new EventTypes.LateDetectedPayload(
            decision.employeeId(),
            decision.date(),
            decision.lateMinutes(),
            decision.streak(),
            decision.counters().lateDaysCount()));
  }

  /** Publishes {@code attendance.autoAbsent.triggered} when a rule escalated the day (step 6). */
  public void publishAutoAbsent(LatenessDecision decision) {
    if (!decision.autoAbsentTriggered()) {
      return;
    }
    publish(
        EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED,
        decision.employeeId(),
        new EventTypes.AutoAbsentPayload(
            decision.employeeId(),
            decision.date(),
            decision.streak(),
            decision.counters().lateDaysCount(),
            EventTypes.AutoAbsentPayload.triggerFor(
                decision.streak(), decision.counters().lateDaysCount())));
  }

  private void publish(String eventType, Long employeeId, Object payload) {
    try {
      String body =
          objectMapper.writeValueAsString(EventEnvelope.of(eventType, PRODUCED_BY, payload));
      rabbitTemplate.convertAndSend(RabbitTopology.ATTENDANCE_EXCHANGE, eventType, body);
      log.info("Published {} for employee {}", eventType, employeeId);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      // Never fail the decision over event serialisation; log loudly instead.
      log.error("Could not serialise {} envelope for employee {}", eventType, employeeId, e);
    }
  }
}
