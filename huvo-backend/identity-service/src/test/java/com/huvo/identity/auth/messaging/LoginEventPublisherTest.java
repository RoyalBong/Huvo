package com.huvo.identity.auth.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.huvo.events.EventTypes;
import com.huvo.identity.auth.entity.AppUser;
import com.huvo.identity.config.RabbitConfig;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The login payload is the input to Section 5.2's lateness engine, so what actually reaches the
 * broker is asserted here rather than left to the integration path.
 */
class LoginEventPublisherTest {

  private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private final LoginEventPublisher publisher =
      new LoginEventPublisher(rabbitTemplate, objectMapper);

  private static AppUser user() {
    AppUser user = new AppUser();
    user.setId(7L);
    user.setEmployeeId(1042L);
    user.setRole("EMPLOYEE");
    return user;
  }

  private EventTypes.LoginPayload capturePayload(OffsetDateTime loginTimestamp, String sourceIp) {
    publisher.publishLoginSuccess(user(), loginTimestamp, sourceIp);
    var captor = ArgumentCaptor.forClass(String.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.IDENTITY_EXCHANGE),
            eq(EventTypes.USER_LOGIN_SUCCESS),
            captor.capture());
    try {
      var envelope = objectMapper.readTree(captor.getValue());
      return objectMapper.treeToValue(envelope.get("payload"), EventTypes.LoginPayload.class);
    } catch (Exception e) {
      throw new AssertionError("The published envelope was not valid JSON", e);
    }
  }

  @Test
  void carriesTheIdsTheEngineNeeds() {
    EventTypes.LoginPayload payload =
        capturePayload(OffsetDateTime.parse("2026-09-27T09:15:00+05:30"), "10.0.0.7");

    // employeeId is what resolves roster -> shift.start_time in Section 5.2 step 2.
    assertThat(payload.userId()).isEqualTo(7L);
    assertThat(payload.employeeId()).isEqualTo(1042L);
    assertThat(payload.role()).isEqualTo("EMPLOYEE");
  }

  @Test
  void carriesTheLoginInstantVerbatim() {
    // The whole point of the field: the value captured in the login method, not the envelope's
    // later occurredAt. If this ever equals the envelope time, the field has been derived wrong.
    OffsetDateTime loginTimestamp = OffsetDateTime.parse("2026-09-27T09:15:00+05:30");

    EventTypes.LoginPayload payload = capturePayload(loginTimestamp, "10.0.0.7");

    assertThat(payload.loginTimestamp()).isEqualTo(loginTimestamp);
  }

  @Test
  void carriesTheClientAddress() {
    EventTypes.LoginPayload payload =
        capturePayload(OffsetDateTime.parse("2026-09-27T09:15:00+05:30"), "10.0.0.7");

    // Section 5.3 records source_ip in login_event for audit; it is not enforced on.
    assertThat(payload.sourceIp()).isEqualTo("10.0.0.7");
  }

  @Test
  void publishesTheContractRoutingKey() {
    publisher.publishLoginSuccess(user(), OffsetDateTime.now(), "10.0.0.7");

    // A key that does not match EventTypes would be silently dropped by the topic exchange.
    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.IDENTITY_EXCHANGE),
            eq(EventTypes.USER_LOGIN_SUCCESS),
            any(String.class));
  }

  @Test
  void toleratesAnAccountWithNoEmployeeRecord() {
    // A bootstrap or service account: the event must still be published so attendance-service
    // can log it, rather than the login failing.
    AppUser unlinked = new AppUser();
    unlinked.setId(1L);
    unlinked.setRole("ADMIN");

    publisher.publishLoginSuccess(unlinked, OffsetDateTime.now(), null);

    verify(rabbitTemplate)
        .convertAndSend(
            eq(RabbitConfig.IDENTITY_EXCHANGE),
            eq(EventTypes.USER_LOGIN_SUCCESS),
            any(String.class));
  }

  @Test
  void prefersTheOriginalClientFromAForwardedChain() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For"))
        .thenReturn("203.0.113.5, 70.41.3.18, 150.172.238.178");
    when(request.getRemoteAddr()).thenReturn("127.0.0.1");

    // Nginx sets proxy_add_x_forwarded_for, so getRemoteAddr() is always loopback; the first
    // entry is the real client and the rest are the proxies it passed through.
    assertThat(LoginEventPublisher.clientIp(request)).isEqualTo("203.0.113.5");
  }

  @Test
  void fallsBackToTheRemoteAddressWhenTheHeaderIsAbsent() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn("192.168.1.20");

    // A test harness calling the service directly, with no proxy in front.
    assertThat(LoginEventPublisher.clientIp(request)).isEqualTo("192.168.1.20");
  }

  @Test
  void fallsBackToTheRemoteAddressWhenTheHeaderIsEmpty() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
    when(request.getRemoteAddr()).thenReturn("192.168.1.20");

    assertThat(LoginEventPublisher.clientIp(request)).isEqualTo("192.168.1.20");
  }

  @Test
  void toleratesNoUsableAddressAtAll() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    when(request.getRemoteAddr()).thenReturn(null);

    // Never throw from a login path just to populate an audit column.
    assertThat(LoginEventPublisher.clientIp(request)).isNull();
  }
}
