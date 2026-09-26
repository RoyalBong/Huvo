package com.huvo.audit;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One row of the shared {@code huvo_audit_log} table (Huvo_Backend_Context.md Sections 3.4, 7).
 *
 * <p>Audit is a library call, not a service and not a queue consumer: every service appends here
 * in-process, so "we log it" cannot rot into a separate deployable that nobody keeps alive.
 *
 * <p>The partition key is {@code entity} so all history for one record is one query, and {@code
 * sortKey} is {@code <occurredAt>#<eventId>} so history sorts chronologically while two writes in
 * the same millisecond stay distinct.
 *
 * @param actorUserId the {@code sub} claim of the caller ({@code null} for a system-initiated
 *     change, e.g. a scheduled job - "who" is not always a person)
 * @param actorRole the caller's access role, captured at write time because a user's role can
 *     change later and the audit trail must show what they were allowed to do <em>then</em>
 * @param action the verb, e.g. {@code employee.updated}
 * @param entity the entity type, e.g. {@code employee} - the partition key
 * @param entityId the id within that entity type
 * @param details free-form context (the ids involved, never credentials or a full entity dump)
 */
public record AuditEntry(
    String actorUserId,
    String actorRole,
    String action,
    String entity,
    String entityId,
    OffsetDateTime occurredAt,
    String eventId,
    String service,
    Map<String, String> details) {

  public static final String PARTITION_KEY = "entity";
  public static final String SORT_KEY = "sortKey";

  /**
   * Builds an entry stamped with the current time and a fresh id. The id is generated here rather
   * than by the caller so a write can never be stored without one - the sort key depends on it.
   *
   * @param actorUserId the caller's user id, or null for a system-initiated change
   * @param actorRole the caller's role, or null when unknown
   * @param action the verb describing what happened
   * @param entity the entity type, which becomes the partition key
   * @param entityId the id within that entity type
   * @param service the calling service name, e.g. {@code identity-service}
   * @param details extra context; null becomes an empty map so no caller has to think about it
   */
  public static AuditEntry of(
      String actorUserId,
      String actorRole,
      String action,
      String entity,
      String entityId,
      String service,
      Map<String, String> details) {
    return new AuditEntry(
        actorUserId,
        actorRole,
        action,
        entity,
        entityId,
        OffsetDateTime.now(),
        java.util.UUID.randomUUID().toString(),
        service,
        details == null ? Map.of() : Map.copyOf(details));
  }

  /**
   * The DynamoDB sort key: time first so history reads chronologically, then the event id so two
   * writes in the same millisecond cannot collide and overwrite one another.
   */
  public String sortKey() {
    return SORT_KEY + "=" + occurredAt.toString() + "#" + eventId;
  }
}
