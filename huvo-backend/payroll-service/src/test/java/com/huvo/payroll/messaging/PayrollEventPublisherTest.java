package com.huvo.payroll.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;

/**
 * Section 7.1, applied from this service's first commit rather than after a mismatch was found.
 *
 * <p>Asserts the bytes on the wire, not a re-serialised copy of the payload. The bare-payload bug -
 * where a producer sent {@code {"employeeId":42}} with no envelope wrapper and the consumer
 * silently dropped every event - compiled cleanly, passed every test in its own service, and was
 * only visible from the consumer's side. That is the whole reason this test exists, and writing it
 * up front is cheaper than rediscovering it.
 */
class PayrollEventPublisherTest {

  /**
   * Boot's mapper: a bare one cannot serialise OffsetDateTime and renders LocalDate as an array.
   */
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Test
  void payrollGeneratedGoesToThePayrollExchangeWithItsOwnRoutingKey() {
    var rabbit =
        org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    var publisher = new PayrollEventPublisher(rabbit, objectMapper);

    publisher.publishPayrollGenerated(
        new EventTypes.PayrollGeneratedPayload(7L, LocalDate.of(2026, 9, 1), 12, "hr-1"));

    org.mockito.Mockito.verify(rabbit)
        .convertAndSend(
            eq(EventTypes.PAYROLL_EXCHANGE), eq(EventTypes.PAYROLL_GENERATED), anyString());
  }

  @Test
  void payrollGeneratedIsWrappedInAnEnvelopeWithItsPayloadUnderThePayloadKey() {
    var rabbit =
        org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    var publisher = new PayrollEventPublisher(rabbit, objectMapper);

    publisher.publishPayrollGenerated(
        new EventTypes.PayrollGeneratedPayload(7L, LocalDate.of(2026, 9, 1), 12, "hr-1"));

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(rabbit)
        .convertAndSend(anyString(), eq(EventTypes.PAYROLL_GENERATED), body.capture());

    // The assertion that would have caught the bare-payload bug: the envelope wrapper is present,
    // so
    // a consumer doing envelope.get("payload") finds something.
    var envelope = readTree(body.getValue());
    assertThat(envelope.get("payload")).isNotNull();
    assertThat(envelope.get("eventType").asText()).isEqualTo(EventTypes.PAYROLL_GENERATED);
    assertThat(envelope.get("producedBy").asText()).isEqualTo("payroll-service");
    assertThat(envelope.get("eventId").asText()).isNotBlank();
  }

  @Test
  void thePayloadCarriesExactlyTheFieldsNotifyServiceWillRead() {
    var rabbit =
        org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    var publisher = new PayrollEventPublisher(rabbit, objectMapper);

    publisher.publishPayrollGenerated(
        new EventTypes.PayrollGeneratedPayload(7L, LocalDate.of(2026, 9, 1), 12, "hr-1"));

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(rabbit)
        .convertAndSend(anyString(), eq(EventTypes.PAYROLL_GENERATED), body.capture());

    // Read twice on purpose: once field by field as a consumer's parser would, and once through the
    // shared payload record. If either drifts, notify-service needs changing with it.
    var payload = readTree(body.getValue()).get("payload");
    var typed = deserialize(body.getValue());

    assertThat(typed.payload().runId()).isEqualTo(7L);
    assertThat(typed.payload().period()).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(typed.payload().payslipCount()).isEqualTo(12);
    assertThat(typed.payload().generatedBy()).isEqualTo("hr-1");
    assertThat(payload.get("period").asText()).isEqualTo("2026-09-01");
  }

  @Test
  void aScheduledRunSendsNoActorRatherThanAPlaceholder() {
    var rabbit =
        org.mockito.Mockito.mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
    var publisher = new PayrollEventPublisher(rabbit, objectMapper);

    publisher.publishPayrollGenerated(
        new EventTypes.PayrollGeneratedPayload(8L, LocalDate.of(2026, 10, 1), 12, null));

    ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
    org.mockito.Mockito.verify(rabbit)
        .convertAndSend(anyString(), eq(EventTypes.PAYROLL_GENERATED), body.capture());

    // null, not "system" or "". A consumer distinguishing a scheduled run from a person needs the
    // absence to survive the wire, and a placeholder string is indistinguishable from a real
    // userId.
    assertThat(readTree(body.getValue()).get("payload").get("generatedBy").isNull()).isTrue();
  }

  private com.fasterxml.jackson.databind.JsonNode readTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /** The same read, through the shared payload type, as notify-service's parser would do it. */
  private EventEnvelope<EventTypes.PayrollGeneratedPayload> deserialize(String json) {
    try {
      return objectMapper.readValue(
          json,
          objectMapper
              .getTypeFactory()
              .constructParametricType(
                  EventEnvelope.class, EventTypes.PayrollGeneratedPayload.class));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
