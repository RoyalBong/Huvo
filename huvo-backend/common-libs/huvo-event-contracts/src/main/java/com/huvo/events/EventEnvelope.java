package com.huvo.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The envelope every published event carries (Huvo_Backend_Context.md Section 7):
 *
 * <pre>
 * {"eventId":"uuid","eventType":"...","occurredAt":"ISO-8601","producedBy":"...","payload":{ }}
 * </pre>
 *
 * <p>Consumers dedupe on {@code eventId}, since RabbitMQ delivery is at-least-once.
 *
 * <p>Lives in this shared module (Section 10) rather than inside a service, because both sides of
 * every event need it: identity-service, worklife-service, payroll-service and notify-service all
 * publish or consume envelopes, and a private copy per service is exactly the drift this module
 * exists to prevent. A change here is a change to the wire contract for every consumer at once -
 * which is the point, but it does mean adding a required field is a coordinated event, not a local
 * refactor.
 *
 * <p>Framework-free on purpose: no Jackson, no Spring. Serialisation is the publisher's job, so a
 * consumer that uses a different JSON library is not forced onto ours.
 */
public record EventEnvelope<T>(
    String eventId, String eventType, OffsetDateTime occurredAt, String producedBy, T payload) {

  public static <T> EventEnvelope<T> of(String eventType, String producedBy, T payload) {
    return new EventEnvelope<>(
        UUID.randomUUID().toString(), eventType, OffsetDateTime.now(), producedBy, payload);
  }
}
