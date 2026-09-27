package com.huvo.notify.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

/**
 * Section 7.1 applied to the consuming side.
 *
 * <p>Section 7.1 says a publisher test must assert against the bytes a real producer sends. It is
 * written for producers, but a consumer has the mirror-image obligation: this test builds each
 * message by running the <em>same</em> serialisation every producer in this backend runs - {@link
 * EventEnvelope} plus an {@code ObjectMapper} configured as Boot configures it - and then asserts
 * the consumer reads it back. A hand-written JSON string would be a fixture that could disagree
 * with production in exactly the ways that matter (a bare payload, a timestamp-array date) while
 * still passing here, which is how the bare-payload bug stayed invisible for so long.
 *
 * <p>The one deliberate difference from {@code WorklifeEventPublisherTest}: this asserts a consumer
 * parsing bytes, that one asserted a producer writing them. Same requirement, opposite direction.
 */
class EnvelopeParserContractTest {

  /**
   * Boot's mapper, not a bare one.
   *
   * <p>A bare {@code new ObjectMapper()} cannot serialise {@code OffsetDateTime} and renders {@code
   * LocalDate} as {@code [2026,9,28]} - so a fixture built with one would assert a wire format
   * production never sends.
   */
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private EnvelopeParser parser;

  @BeforeEach
  void setUp() {
    parser = new EnvelopeParser(objectMapper);
  }

  /** Exactly what a producer puts on the wire. */
  private String produce(String eventType, Object payload) {
    try {
      return objectMapper.writeValueAsString(EventEnvelope.of(eventType, "some-service", payload));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void readsALateDetectedEventAsItsSharedPayloadType() {
    String message =
        produce(
            EventTypes.ATTENDANCE_LATE_DETECTED,
            new EventTypes.LateDetectedPayload(42L, LocalDate.of(2026, 9, 28), 17, 3, 2));

    EventEnvelope<EventTypes.LateDetectedPayload> envelope =
        parser.parse(message, EventTypes.LateDetectedPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.eventType()).isEqualTo(EventTypes.ATTENDANCE_LATE_DETECTED);
    assertThat(envelope.producedBy()).isEqualTo("some-service");
    assertThat(envelope.eventId()).isNotBlank();
    assertThat(envelope.occurredAt()).isNotNull();

    var payload = envelope.payload();
    assertThat(payload.employeeId()).isEqualTo(42L);
    assertThat(payload.date()).isEqualTo(LocalDate.of(2026, 9, 28));
    assertThat(payload.lateMinutes()).isEqualTo(17);
    assertThat(payload.streak()).isEqualTo(3);
    assertThat(payload.lateDaysCount()).isEqualTo(2);
  }

  @Test
  void readsAnAutoAbsentEventAsItsSharedPayloadType() {
    String message =
        produce(
            EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED,
            new EventTypes.AutoAbsentPayload(
                42L, LocalDate.of(2026, 9, 28), 3, 2, EventTypes.AutoAbsentPayload.TRIGGER_STREAK));

    var envelope = parser.parse(message, EventTypes.AutoAbsentPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().triggeredBy())
        .isEqualTo(EventTypes.AutoAbsentPayload.TRIGGER_STREAK);
    assertThat(envelope.payload().streak()).isEqualTo(3);
  }

  @Test
  void readsALeaveApprovedEventAsItsSharedPayloadType() {
    // The same four fields attendance-service's own listener destructures by hand. Here they are
    // read
    // through a compile-time type instead, so a rename is a build failure rather than a null.
    String message =
        produce(
            EventTypes.LEAVE_APPROVED,
            new EventTypes.LeaveApprovedPayload(
                42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    var envelope = parser.parse(message, EventTypes.LeaveApprovedPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().employeeId()).isEqualTo(42L);
    assertThat(envelope.payload().from()).isEqualTo(LocalDate.of(2026, 9, 28));
    assertThat(envelope.payload().to()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(envelope.payload().leaveId()).isEqualTo(7L);
  }

  @Test
  void readsATaskAssignedEventAsItsSharedPayloadType() {
    OffsetDateTime deadline = OffsetDateTime.of(2026, 9, 30, 11, 30, 0, 0, ZoneOffset.UTC);
    String message =
        produce(
            EventTypes.TASK_ASSIGNED,
            new EventTypes.TaskAssignedPayload(1L, 42L, "mgr-1", "Write the report", deadline));

    var envelope = parser.parse(message, EventTypes.TaskAssignedPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().taskId()).isEqualTo(1L);
    assertThat(envelope.payload().employeeId()).isEqualTo(42L);
    assertThat(envelope.payload().assignedByUserId()).isEqualTo("mgr-1");
    assertThat(envelope.payload().title()).isEqualTo("Write the report");
    assertThat(envelope.payload().deadline()).isEqualTo(deadline);
  }

  @Test
  void readsATaskWithNoDeadlineAsNullRatherThanFailing() {
    // An open-ended task is a legitimate absence, not a contract violation. The distinction between
    // "field not present" and "field present but wrong" is why the payload is a typed record.
    String message =
        produce(
            EventTypes.TASK_ASSIGNED,
            new EventTypes.TaskAssignedPayload(1L, 42L, "mgr-1", "Whenever", null));

    var envelope = parser.parse(message, EventTypes.TaskAssignedPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().deadline()).isNull();
  }

  @Test
  void readsATaskOverdueEventAsItsSharedPayloadType() {
    String message =
        produce(
            EventTypes.TASK_OVERDUE,
            new EventTypes.TaskOverduePayload(1L, 42L, "Write the report", null));

    var envelope = parser.parse(message, EventTypes.TaskOverduePayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().taskId()).isEqualTo(1L);
    assertThat(envelope.payload().title()).isEqualTo("Write the report");
  }

  @Test
  void readsATaskSubmittedLateEventAsItsSharedPayloadType() {
    OffsetDateTime submittedAt = OffsetDateTime.of(2026, 9, 30, 12, 0, 0, 0, ZoneOffset.UTC);
    String message =
        produce(
            EventTypes.TASK_SUBMITTED_LATE,
            new EventTypes.TaskSubmittedLatePayload(
                1L, 42L, "Write the report", null, submittedAt));

    var envelope = parser.parse(message, EventTypes.TaskSubmittedLatePayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().submittedAt()).isEqualTo(submittedAt);
  }

  @Test
  void readsALeaveRejectedEventAsItsSharedPayloadType() {
    String message =
        produce(
            EventTypes.LEAVE_REJECTED,
            new EventTypes.LeaveRejectedPayload(
                42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L, "Quarter close"));

    var envelope = parser.parse(message, EventTypes.LeaveRejectedPayload.class);

    assertThat(envelope).isNotNull();
    assertThat(envelope.payload().reason()).isEqualTo("Quarter close");
  }

  // --- the cases that must fail rather than quietly return nulls ---

  @Test
  void aBarePayloadIsRejectedRatherThanReadAsAnEnvelope() {
    // THE regression test for the bug that shipped. Produced by serialising the payload on its own,
    // with no envelope around it - exactly what WorklifeEventPublisher did before it was fixed. The
    // old hand-parsed listener logged "envelope carried no payload", dropped the event, and looked
    // healthy while nobody was ever marked ON_LEAVE.
    String trulyBare =
        serialise(
            new EventTypes.LeaveApprovedPayload(
                42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    assertThat(trulyBare).doesNotContain("payload");
    assertThat(parser.parse(trulyBare, EventTypes.LeaveApprovedPayload.class)).isNull();
  }

  @Test
  void aPayloadMissingARequiredFieldIsRejectedRatherThanDefaulted() {
    // The other half of the same defect. JsonNode's get(...).asLong() would have quietly returned 0
    // here and produced a notification about employee 0, or a null that blew up three layers away.
    String message =
        "{\"eventId\":\"e1\",\"eventType\":\"task.assigned\",\"payload\":{\"taskId\":1}}";

    assertThat(parser.parse(message, EventTypes.TaskAssignedPayload.class)).isNull();
  }

  @Test
  void anUnreadableDateFormatIsRejectedRatherThanParsed() {
    // A producer that switched to timestamp arrays would otherwise yield a null date here, and a
    // null
    // date in an attendance notification is a message saying nothing.
    String message =
        "{\"eventId\":\"e1\",\"eventType\":\"x\",\"payload\":{\"employeeId\":42,"
            + "\"date\":\"not-a-date\",\"lateMinutes\":5,\"streak\":1,\"lateDaysCount\":1}}";

    assertThat(parser.parse(message, EventTypes.LateDetectedPayload.class)).isNull();
  }

  @Test
  void garbageIsRejectedRatherThanThrowing() {
    // A poison message must not stall the queue behind it, so parsing returns null and the listener
    // drops it rather than requeueing forever.
    assertThat(parser.parse("not json at all", EventTypes.LeaveAppliedPayload.class)).isNull();
    assertThat(parser.parse("", EventTypes.LeaveAppliedPayload.class)).isNull();
  }

  @Test
  void parseOrThrowIsAvailableWhereAMissingEventShouldBeFatal() {
    String message =
        produce(
            EventTypes.LEAVE_APPLIED,
            new EventTypes.LeaveAppliedPayload(
                42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    assertThat(
            parser.parseOrThrow(message, EventTypes.LeaveAppliedPayload.class).payload().leaveId())
        .isEqualTo(7L);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> parser.parseOrThrow("{", EventTypes.LeaveAppliedPayload.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private String serialise(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
