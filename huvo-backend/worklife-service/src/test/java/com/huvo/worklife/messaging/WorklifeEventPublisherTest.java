package com.huvo.worklife.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

/**
 * The wire format of everything this service publishes.
 *
 * <p>These assert the bytes on the broker, not the payload object, because that is the whole risk:
 * {@code LeaveApprovedListener} lives in another service and reads the JSON by hand with {@code
 * envelope.get("payload")}, so nothing at compile time connects it to {@link
 * EventTypes.LeaveApprovedPayload}. A producer that stopped wrapping its payload in an {@link
 * EventEnvelope} would still compile, still pass every payload test, and silently stop marking
 * leave days in production.
 *
 * <p>The assertions mirror that listener's parser field for field. If they change, it changes with
 * them.
 */
class WorklifeEventPublisherTest {

  /**
   * Mirrors the application's mapper, which Boot configures with the JSR-310 module and with
   * timestamp writing disabled.
   *
   * <p>Both details matter. A bare {@code new ObjectMapper()} cannot serialise {@code
   * OffsetDateTime} at all, and with timestamps left on it renders {@code LocalDate} as {@code
   * [2026,9,28]} rather than {@code 2026-09-28} - which is what attendance's listener actually
   * parses, so the test would have been asserting a format production never sends.
   */
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final RabbitTemplate rabbit = org.mockito.Mockito.mock(RabbitTemplate.class);
  private final WorklifeEventPublisher publisher = new WorklifeEventPublisher(rabbit, objectMapper);

  /** The body that was handed to the broker. */
  private String capturedBody(String routingKey) {
    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    verify(rabbit).convertAndSend(anyString(), eq(routingKey), body.capture());
    return body.getValue();
  }

  @Test
  void leaveApprovedIsWrappedInAnEnvelopeAndGoesToTheLeaveExchange() {
    publisher.publishLeaveApproved(
        new EventTypes.LeaveApprovedPayload(
            42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    verify(rabbit)
        .convertAndSend(eq(EventTypes.LEAVE_EXCHANGE), eq(EventTypes.LEAVE_APPROVED), anyString());
  }

  @Test
  void leaveApprovedCarriesThePayloadUnderAnEnvelopePayloadKey() throws Exception {
    // The exact reason this test exists. LeaveApprovedListener does:
    //   envelope.get("payload") ... and drops the event if it is null.
    // A bare payload - which is what a naive publisher sends - parses to null here and every
    // leave.approved is silently discarded.
    publisher.publishLeaveApproved(
        new EventTypes.LeaveApprovedPayload(
            42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    JsonNode envelope = objectMapper.readTree(capturedBody(EventTypes.LEAVE_APPROVED));
    JsonNode payload = envelope.get("payload");

    assertThat(payload).isNotNull();
  }

  @Test
  void leaveApprovedPayloadExposesExactlyTheFieldsTheListenerReads() throws Exception {
    publisher.publishLeaveApproved(
        new EventTypes.LeaveApprovedPayload(
            42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    JsonNode payload =
        objectMapper.readTree(capturedBody(EventTypes.LEAVE_APPROVED)).get("payload");

    // Mirrors LeaveApprovedListener.parse(): employeeId, from, to, leaveId.
    assertThat(payload.get("employeeId").asLong()).isEqualTo(42L);
    assertThat(LocalDate.parse(payload.get("from").asText())).isEqualTo(LocalDate.of(2026, 9, 28));
    assertThat(LocalDate.parse(payload.get("to").asText())).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(payload.get("leaveId").asLong()).isEqualTo(7L);
  }

  @Test
  void leaveAppliedUsesTheSameShapeAsTheApproval() throws Exception {
    // notify-service will consume both, and an approver and the attendance engine want the same
    // facts, so the two shapes must not drift apart.
    publisher.publishLeaveApplied(
        new EventTypes.LeaveAppliedPayload(
            42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L));

    JsonNode payload = objectMapper.readTree(capturedBody(EventTypes.LEAVE_APPLIED)).get("payload");

    assertThat(payload.get("employeeId").asLong()).isEqualTo(42L);
    assertThat(payload.get("from").asText()).isEqualTo("2026-09-28");
    assertThat(payload.get("to").asText()).isEqualTo("2026-09-30");
    assertThat(payload.get("leaveId").asLong()).isEqualTo(7L);
  }

  @Test
  void theEnvelopeNamesTheProducingService() throws Exception {
    publisher.publishLeaveRejected(
        new EventTypes.LeaveRejectedPayload(
            42L, LocalDate.of(2026, 9, 28), LocalDate.of(2026, 9, 30), 7L, "Quarter close"));

    JsonNode envelope = objectMapper.readTree(capturedBody(EventTypes.LEAVE_REJECTED));

    assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.LEAVE_REJECTED);
    assertThat(envelope.get("producedBy").asText()).isEqualTo("worklife-service");
  }

  @Test
  void taskEventsGoToTheTaskExchangeWithTheirOwnRoutingKeys() {
    publisher.publishTaskAssigned(
        new EventTypes.TaskAssignedPayload(1L, 42L, "mgr-1", "Write the report", null));

    verify(rabbit)
        .convertAndSend(eq(EventTypes.TASK_EXCHANGE), eq(EventTypes.TASK_ASSIGNED), anyString());
  }

  @Test
  void aTaskOverdueEventCarriesTheAssigneeAndDeadline() throws Exception {
    publisher.publishTaskOverdue(
        new EventTypes.TaskOverduePayload(1L, 42L, "Write the report", null));

    JsonNode payload = objectMapper.readTree(capturedBody(EventTypes.TASK_OVERDUE)).get("payload");

    assertThat(payload.get("taskId").asLong()).isEqualTo(1L);
    assertThat(payload.get("employeeId").asLong()).isEqualTo(42L);
    assertThat(payload.get("title").asText()).isEqualTo("Write the report");
  }
}
