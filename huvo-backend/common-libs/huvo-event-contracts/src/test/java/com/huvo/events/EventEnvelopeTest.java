package com.huvo.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

  @Test
  void stampsAFreshEventIdOnEveryEnvelope() {
    EventEnvelope<String> first = EventEnvelope.of("employee.created", "identity-service", "1");
    EventEnvelope<String> second = EventEnvelope.of("employee.created", "identity-service", "1");

    // Dedupe is by eventId, so two publishes of the same fact must not collide.
    assertThat(first.eventId()).isNotEqualTo(second.eventId());
    assertThat(UUID.fromString(first.eventId())).isNotNull();
  }

  @Test
  void carriesTheProducerAndTypeItWasGiven() {
    EventEnvelope<Long> envelope = EventEnvelope.of("department.created", "identity-service", 42L);

    assertThat(envelope.eventType()).isEqualTo("department.created");
    assertThat(envelope.producedBy()).isEqualTo("identity-service");
    assertThat(envelope.payload()).isEqualTo(42L);
  }

  @Test
  void stampsOccurredAtAsAnOffsetDateTime() {
    OffsetDateTime before = OffsetDateTime.now();
    EventEnvelope<Long> envelope = EventEnvelope.of("employee.updated", "identity-service", 1L);
    OffsetDateTime after = OffsetDateTime.now();

    // OffsetDateTime, not LocalDateTime: producers and consumers sit in different regions.
    assertThat(envelope.occurredAt()).isBetween(before, after);
  }

  @Test
  void allowsANullPayloadForEventsThatAreOnlyASignal() {
    // A delete event may carry no body, and the envelope must not reject that.
    EventEnvelope<Void> envelope = EventEnvelope.of("employee.deleted", "identity-service", null);

    assertThat(envelope.payload()).isNull();
    assertThat(envelope.eventType()).isEqualTo("employee.deleted");
  }

  @Test
  void isAGenericEnvelopeSoOneTypeCoversEveryPayload() {
    // A consumer reading a mixed queue deserialises the same envelope with a different T.
    EventEnvelope<EventTypes.LoginPayload> login =
        EventEnvelope.of(
            EventTypes.USER_LOGIN_SUCCESS,
            "identity-service",
            new EventTypes.LoginPayload(
                1L, 2L, "HR", OffsetDateTime.parse("2026-09-27T09:15:00+05:30"), "10.0.0.7"));
    EventEnvelope<Long> employee = EventEnvelope.of("employee.created", "identity-service", 7L);

    assertThat(login.payload().role()).isEqualTo("HR");
    assertThat(employee.payload()).isNotInstanceOf(EventTypes.LoginPayload.class);
  }
}
