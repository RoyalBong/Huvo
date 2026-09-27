package com.huvo.notify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.notify.messaging.EnvelopeParser;
import com.huvo.notify.websocket.WebSocketHub;

/**
 * notify-service (Huo_Backend_Context.md Sections 3.1, 3.2, 7, 12).
 *
 * <p>Consumes events from every other service, builds the in-app notification feed, and is the
 * single WebSocket hub the frontend multiplexes everything real-time through
 * (Huvo_Frontend_Context.md Section 5.3).
 *
 * <p>No database. Section 3.2 puts notifications in DynamoDB because the access pattern is simple
 * and write-heavy and needs no joins, so this service carries no schema and no Flyway migration.
 */
@SpringBootApplication
public class NotifyApplication {

  public static void main(String[] args) {
    SpringApplication.run(NotifyApplication.class, args);
  }

  /**
   * One parser for every listener, so all of them bind through the same typed path.
   *
   * <p>Takes the application's {@code ObjectMapper} rather than constructing one. That matters: a
   * mapper built here without Boot's modules cannot serialise {@code OffsetDateTime} and renders
   * {@code LocalDate} as an array, which is precisely the sort of wire-format drift the shared
   * parser exists to prevent.
   */
  @Bean
  EnvelopeParser envelopeParser(ObjectMapper objectMapper) {
    return new EnvelopeParser(objectMapper);
  }

  /**
   * The single hub every socket registers with.
   *
   * <p>One bean, so the whole process shares one registry - the frontend's "one connection, not
   * five" promise is only true if there is genuinely one hub.
   */
  @Bean
  WebSocketHub webSocketHub() {
    return new WebSocketHub();
  }
}
