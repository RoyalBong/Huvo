package com.huvo.notify.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;

import lombok.extern.slf4j.Slf4j;

/**
 * Turns a raw broker message into a typed {@link EventEnvelope} carrying a shared payload record.
 *
 * <p>This class is the answer to a bug that has now appeared twice. {@code LeaveApprovedListener}
 * read its message with {@code JsonNode} and {@code payload.get("employeeId").asLong()}, which
 * returns {@code 0} for a missing field rather than failing. A producer that sent a bare payload
 * instead of an envelope therefore produced a listener that logged "envelope carried no payload",
 * dropped the event, and looked entirely healthy - no employee was ever marked {@code ON_LEAVE},
 * and nothing in either service's own tests could see it.
 *
 * <p>Binding to {@code EventEnvelope<T>} with {@code T} a {@code huvo-event-contracts} record makes
 * the same mistake a deserialization failure instead: Jackson cannot map {@code [2026,9,28]} to a
 * {@code LocalDate} or an absent {@code leaveId} to a {@code Long} without complaining, and the
 * error names the field. The contract is then enforced by the compiler on the consuming side, not
 * by a comment.
 *
 * <p>Failures are logged and swallowed rather than rethrown: requeueing a malformed message
 * poison-loops the queue, and a single unparseable event must not stall every notification behind
 * it. The event is dropped and the operator sees why.
 */
@Slf4j
public class EnvelopeParser {

  private final ObjectMapper objectMapper;

  /**
   * The application's mapper with this boundary's strictness added.
   *
   * <p>Jackson does <strong>not</strong> fail on a missing constructor property by default - a
   * {@code TaskAssignedPayload} sent with only {@code taskId} deserialises happily with {@code
   * employeeId}, {@code title} and {@code deadline} all null. That is precisely the silent
   * wrong-data failure this class exists to prevent, and binding to a typed record alone does not
   * prevent it. Enabling the flag is what turns "wrong shape" into "rejected", so a drifted
   * producer gets a named error instead of a notification about nothing.
   *
   * <p>A copy rather than a mutation of the injected mapper, because the application mapper is
   * shared with the REST layer and this strictness belongs only at the broker boundary.
   */
  private final ObjectMapper strictMapper;

  public EnvelopeParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.strictMapper =
        objectMapper.copy().enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
  }

  /**
   * Deserializes a message into a typed envelope, or null when it cannot be read.
   *
   * @param message the raw broker message
   * @param payloadType the shared payload record this event carries
   * @param <T> the payload type
   * @return the typed envelope, or null if the message is malformed
   */
  public <T> EventEnvelope<T> parse(String message, Class<T> payloadType) {
    try {
      EventEnvelope<T> envelope =
          strictMapper
              .reader(
                  strictMapper
                      .getTypeFactory()
                      .constructParametricType(EventEnvelope.class, payloadType))
              .readValue(message);
      if (envelope == null || envelope.payload() == null) {
        // A bare payload rather than an envelope lands here. Named explicitly, because this is the
        // exact shape that broke the leave path once already.
        log.error(
            "Event of type {} carried no payload object. The producer sent a bare payload where an "
                + "EventEnvelope was expected.",
            envelope == null ? "unknown" : envelope.eventType());
        return null;
      }
      return envelope;
    } catch (Exception e) {
      log.error("Dropping unparseable {} event", payloadType.getSimpleName(), e);
      return null;
    }
  }

  /**
   * Deserializes a message, requiring a result.
   *
   * @param message the raw broker message
   * @param payloadType the shared payload record this event carries
   * @param <T> the payload type
   * @return the typed envelope, never null
   * @throws IllegalArgumentException if the message is malformed
   */
  public <T> EventEnvelope<T> parseOrThrow(String message, Class<T> payloadType) {
    EventEnvelope<T> envelope = parse(message, payloadType);
    if (envelope == null) {
      throw new IllegalArgumentException(
          "Could not read a " + payloadType.getSimpleName() + " event from the message");
    }
    return envelope;
  }
}
