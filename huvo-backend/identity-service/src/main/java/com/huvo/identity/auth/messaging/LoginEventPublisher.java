package com.huvo.identity.auth.messaging;

import java.time.OffsetDateTime;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;
import com.huvo.identity.auth.entity.AppUser;
import com.huvo.identity.config.RabbitConfig;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes {@code user.login.success} on the identity exchange (Huvo_Backend_Context.md Sections
 * 5.2, 7). attendance-service consumes it to run the lateness engine; the payload carries only ids
 * and metadata, never credentials.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginEventPublisher {

  /** One place that names the service for the {@code producedBy} envelope field. */
  public static final String PRODUCED_BY = "identity-service";

  private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

  private static final int MAX_IP_LENGTH = 255;

  private final RabbitTemplate rabbitTemplate;
  private final ObjectMapper objectMapper;

  /**
   * @param user the account that authenticated
   * @param loginTimestamp when the credentials were verified, taken inside the login method rather
   *     than from the envelope - Section 5.2 computes the lateness delta from it
   * @param sourceIp the client address, for the {@code login_event} audit columns in Section 5.3
   */
  public void publishLoginSuccess(AppUser user, OffsetDateTime loginTimestamp, String sourceIp) {
    EventEnvelope<EventTypes.LoginPayload> envelope =
        EventEnvelope.of(
            EventTypes.USER_LOGIN_SUCCESS,
            PRODUCED_BY,
            new EventTypes.LoginPayload(
                user.getId(), user.getEmployeeId(), user.getRole(), loginTimestamp, sourceIp));
    try {
      rabbitTemplate.convertAndSend(
          RabbitConfig.IDENTITY_EXCHANGE,
          EventTypes.USER_LOGIN_SUCCESS,
          objectMapper.writeValueAsString(envelope));
      log.info("Published {} for user {}", EventTypes.USER_LOGIN_SUCCESS, user.getId());
    } catch (JsonProcessingException e) {
      // Never fail the login over event serialisation; log loudly instead.
      log.error(
          "Could not serialise {} envelope for user {}",
          EventTypes.USER_LOGIN_SUCCESS,
          user.getId(),
          e);
    }
  }

  /**
   * The client address as the edge proxy saw it, preferring {@code X-Forwarded-For} over {@link
   * HttpServletRequest#getRemoteAddr()} (Sections 3.2, 5.3).
   *
   * <p>Nginx is always in front of these services and sets {@code X-Forwarded-For} via {@code
   * proxy_add_x_forwarded_for}, so {@code getRemoteAddr()} alone is always loopback and would make
   * every row in {@code login_event} look like it came from 127.0.0.1. The header is taken as
   * authoritative: {@code source_ip} is audit metadata that Section 5.3 explicitly does not enforce
   * on, so the spoof risk is limited to a misleading column rather than a security decision.
   *
   * <p>Takes the <em>first</em> address in the chain, the original client, because anything a later
   * hop appended is that proxy. A malformed or empty header falls back to the remote address rather
   * than failing a login.
   *
   * @param request the in-flight login request
   * @return the client IP, or null when neither source yields one
   */
  public static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
    if (forwarded != null && !forwarded.isBlank()) {
      String first = forwarded.split(",")[0].trim();
      if (!first.isEmpty()) {
        return truncate(first);
      }
    }
    String remote = request.getRemoteAddr();
    return remote == null || remote.isBlank() ? null : truncate(remote);
  }

  /** An address is a handful of characters; the cap only guards a hostile header. */
  private static String truncate(String value) {
    return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
  }
}
