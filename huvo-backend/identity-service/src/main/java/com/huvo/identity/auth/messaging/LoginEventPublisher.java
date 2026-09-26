package com.huvo.identity.auth.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.identity.auth.entity.AppUser;
import com.huvo.identity.config.RabbitConfig;
import com.huvo.identity.event.EventEnvelope;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes {@code user.login.success} on the identity exchange (Huvo_Backend_Context.md Sections
 * 5.2, 7). attendance-service consumes it to run the lateness engine; the payload carries only ids,
 * never credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventPublisher {

  /** One place that names the service for the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "identity-service";

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  public void publishLoginSuccess(AppUser user) {
    var payload = new LoginPayload(user.getId(), user.getEmployeeId(), user.getRole());
    EventEnvelope<LoginPayload> envelope =
        EventEnvelope.of("user.login.success", PRODUCED_BY, payload);
    try {
      rabbitTemplate.convertAndSend(
          RabbitConfig.IDENTITY_EXCHANGE,
          "user.login.success",
          objectMapper.writeValueAsString(envelope));
      log.info("Published user.login.success for user {}", user.getId());
    } catch (JsonProcessingException e) {
      // Never fail the login over event serialisation; log loudly instead.
      log.error("Could not serialise user.login.success envelope for user {}", user.getId(), e);
    }
  }

  /** Minimal login fact: who logged in, which employee profile, with what role. */
  public record LoginPayload(Long userId, Long employeeId, String role) {}
}
