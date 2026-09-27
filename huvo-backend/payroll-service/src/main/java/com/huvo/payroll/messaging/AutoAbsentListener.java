package com.huvo.payroll.messaging;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

import lombok.extern.slf4j.Slf4j;

/**
 * Consumes {@code attendance.autoAbsent.triggered} (Section 7).
 *
 * <p><b>This listener records and does nothing else, on purpose.</b> Section 7 lists
 * payroll-service as a consumer of this event and marks it "(future)", and the context document
 * specifies no rule anywhere that connects an absence to a pay deduction. Inventing one would mean
 * guessing at whether an unpaid day is the daily rate or a flat deduction, whether it applies to a
 * day already marked absent, and whether a manager's correction reverses it. Every one of those is
 * a payroll policy question, and getting any of them wrong produces a payslip that is confidently
 * incorrect.
 *
 * <p>So the event is parsed, logged at a level that makes it countable, and dropped. The parse is
 * still worth doing: it is the cheapest possible proof that payroll can read the bytes attendance
 * actually sends, and that proof is worth having before the day a deduction rule needs it.
 *
 * <p>Bound to the shared payload record rather than parsed by hand, per the lesson from {@code
 * LeaveApprovedListener}. Jackson does not fail on a missing constructor property by default, so
 * this reads through a mapper copy with that enabled - a shape drift is a named error rather than a
 * null that quietly means "no absence".
 */
@Slf4j
@Component
public class AutoAbsentListener {

  /** This service's private queue on the attendance exchange. */
  public static final String QUEUE = "payroll.auto-absent-consume";

  private final ObjectMapper objectMapper;

  /** Strict for the same reason as notify-service's parser: see the class comment. */
  private final ObjectMapper strict;

  /**
   * @param objectMapper the application's mapper, so this listener reads with the same
   *     configuration producers write with rather than a hand-built one that disagrees about date
   *     rendering
   */
  public AutoAbsentListener(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.strict =
        objectMapper.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
  }

  @RabbitListener(queues = QUEUE)
  public void onAutoAbsent(String message) {
    EventEnvelope<EventTypes.AutoAbsentPayload> envelope;
    try {
      envelope =
          strict
              .reader(
                  strict
                      .getTypeFactory()
                      .constructParametricType(
                          EventEnvelope.class, EventTypes.AutoAbsentPayload.class))
              .readValue(message);
    } catch (Exception e) {
      // Log and drop: requeueing a malformed event would poison-loop the queue.
      log.error("Dropping unparseable attendance.autoAbsent.triggered event", e);
      return;
    }
    if (envelope == null || envelope.payload() == null) {
      log.error("attendance.autoAbsent.triggered carried no payload object");
      return;
    }

    var payload = envelope.payload();
    // At INFO, not DEBUG, so an operator can count absences actually seen by payroll. That count is
    // the thing a future deduction rule will need reconciled against.
    log.info(
        "Observed absence: employee={} date={} streak={} triggeredBy={} (no pay effect today - "
            + "no deduction rule is specified)",
        payload.employeeId(),
        payload.date(),
        payload.streak(),
        payload.triggeredBy());
  }
}
