package com.huvo.identity.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The envelope every published event carries (Huvo_Backend_Context.md Section 7):
 *
 * <pre>{"eventId":"uuid","eventType":"...","occurredAt":"ISO-8601","producedBy":"...","payload":{ }}</pre>
 *
 * <p>Consumers dedupe on {@code eventId}, since RabbitMQ delivery is at-least-once.
 *
 * <p>TODO: move to {@code common-libs/huvo-event-contracts} (Section 10) once a second
 * service publishes events and the contract genuinely needs to be shared.
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        OffsetDateTime occurredAt,
        String producedBy,
        T payload) {

    public static <T> EventEnvelope<T> of(String eventType, String producedBy, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                OffsetDateTime.now(),
                producedBy,
                payload);
    }
}
