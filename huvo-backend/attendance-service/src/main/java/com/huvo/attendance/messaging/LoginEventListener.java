package com.huvo.attendance.messaging;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.attendance.login.entity.LoginEvent;
import com.huvo.attendance.login.repository.LoginEventRepository;

import jakarta.annotation.PreDestroy;

/**
 * Consumes {@code user.login.success} from identity-service and drives the lateness engine
 * (Huvo_Backend_Context.md Sections 5.2, 7).
 *
 * <p>Order is deliberate and load-bearing: the raw {@code login_event} row is written <b>before</b>
 * any lateness logic runs. Section 5.1 calls that table immutable and append-only precisely so the
 * facts exist independently of the rules engine - if the engine is later found to have been wrong,
 * the logins are still there to replay it against. The other way round, a crash inside the engine
 * would lose both the decision and the evidence for it.
 *
 * <p>Idempotent by {@code eventId} (Section 7 promises at-least-once): a redelivery is recognised
 * by the unique {@code source_event_id} and dropped, so the engine never runs twice for one login.
 *
 * <p>Owns its own queue binding; the publisher has no idea this listener exists.
 */
@Component
public class LoginEventListener {

  private static final Logger log = LoggerFactory.getLogger(LoginEventListener.class);

  private final ObjectMapper objectMapper;
  private final LoginEventRepository loginEvents;
  private final AttendanceEvaluationService evaluator;
  private final AttendanceEventPublisher publisher;

  /**
   * Event ids this instance has processed, as a cheap fast path in front of the database check. The
   * unique index on {@code source_event_id} is the real guard, since it survives a restart and a
   * second instance; this set is only there to avoid the query per message.
   */
  private final Set<String> seenEventIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public LoginEventListener(
      ObjectMapper objectMapper,
      LoginEventRepository loginEvents,
      AttendanceEvaluationService evaluator,
      AttendanceEventPublisher publisher) {
    this.objectMapper = objectMapper;
    this.loginEvents = loginEvents;
    this.evaluator = evaluator;
    this.publisher = publisher;
  }

  @RabbitListener(queues = RabbitTopology.LOGIN_QUEUE)
  public void onLogin(String message) {
    LoginFacts facts;
    try {
      facts = parse(message);
    } catch (Exception e) {
      // Log and drop: requeueing a malformed event would poison-loop the queue forever.
      log.error("Dropping unparseable login event", e);
      return;
    }
    if (facts.eventId() != null && alreadyProcessed(facts.eventId())) {
      log.info("Skipping already-processed login event {}", facts.eventId());
      return;
    }
    recordFact(facts);
    runEngine(facts);
  }

  /** The parts of a login event the rules need, already extracted. */
  record LoginFacts(
      String eventId, Long employeeId, OffsetDateTime loginTimestamp, String sourceIp) {}

  private LoginFacts parse(String message) throws Exception {
    JsonNode envelope = objectMapper.readTree(message);
    JsonNode payload = envelope.get("payload");
    Long employeeId =
        payload == null || payload.get("employeeId") == null || payload.get("employeeId").isNull()
            ? null
            : payload.get("employeeId").asLong();
    OffsetDateTime loginAt =
        payload == null || payload.get("loginTimestamp") == null
            ? null
            : OffsetDateTime.parse(payload.get("loginTimestamp").asText());
    return new LoginFacts(
        text(envelope, "eventId"),
        employeeId,
        loginAt,
        payload == null ? null : text(payload, "sourceIp"));
  }

  private static String text(JsonNode node, String field) {
    return node.get(field) == null || node.get(field).isNull() ? null : node.get(field).asText();
  }

  private boolean alreadyProcessed(String eventId) {
    return seenEventIds.contains(eventId) || loginEvents.existsBySourceEventId(eventId);
  }

  /**
   * Writes the immutable fact row, before any rule runs.
   *
   * <p>Local time is stored rather than the offset: {@code login_event} holds a {@code DATETIME}
   * and attendance is a wall-clock concept, which is the same reasoning as {@code
   * shift.start_time}.
   */
  @Transactional
  void recordFact(LoginFacts facts) {
    if (facts.eventId() != null && loginEvents.existsBySourceEventId(facts.eventId())) {
      return;
    }
    LoginEvent event = new LoginEvent();
    event.setEmployeeId(facts.employeeId());
    event.setLoginTimestamp(
        facts.loginTimestamp() == null ? null : facts.loginTimestamp().toLocalDateTime());
    event.setSourceIp(facts.sourceIp());
    event.setSourceEventId(facts.eventId());
    loginEvents.save(event);
    if (facts.eventId() != null) {
      seenEventIds.add(facts.eventId());
    }
  }

  /**
   * Runs the rules and publishes whatever they decided.
   *
   * <p>Skipped when there is no employee record: a service or bootstrap account has no shift to be
   * late to, and the fact row above is all that is meaningful for it.
   */
  private void runEngine(LoginFacts facts) {
    if (facts.employeeId() == null || facts.loginTimestamp() == null) {
      log.info(
          "Login event {} has no employee record; fact recorded, rules not run", facts.eventId());
      return;
    }
    var decision = evaluator.evaluateAndPersist(facts.employeeId(), facts.loginTimestamp());
    publisher.publishLateDetected(decision);
    publisher.publishAutoAbsent(decision);
    log.info(
        "Evaluated login event {}: employee {} on {} -> {}",
        facts.eventId(),
        decision.employeeId(),
        decision.date(),
        decision.outcome());
  }

  @PreDestroy
  void clearSeenEvents() {
    // The set lives only as long as the process; the unique index is the durable guard.
    seenEventIds.clear();
  }
}
