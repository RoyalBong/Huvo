package com.huvo.identity.audit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.huvo.audit.AuditClient;
import com.huvo.audit.AuditEntry;
import com.huvo.audit.AuditWriteException;
import com.huvo.security.HuvoPrincipal;

/**
 * Writes an audit row for every employee and department mutation (Huvo_Backend_Context.md Sections
 * 3.4, 7).
 *
 * <p>This is the identity-service adapter over the shared {@code huvo-audit-client}: the client is
 * the library, the principal is read from the security context here because the library stays
 * framework-free. Audit is deliberately <em>not</em> a Spring AOP aspect around the services — an
 * explicit call at each mutation site is auditable by reading the code, whereas an aspect is
 * invisible and easy to forget when a new mutation is added.
 *
 * <p>The actor is the authenticated caller, captured at write time: who did it and with what role,
 * because a role can change later and the trail must show what they were allowed to do then.
 *
 * <p><b>Two recorders, because a best-effort trail and a guaranteed one are different
 * contracts.</b> {@link #record} is best-effort: a DynamoDB failure is logged and swallowed,
 * because the mutation has already committed and failing the request afterwards would report a
 * database rollback that never happened. It is correct for the six employee and department CRUD
 * paths here, but it makes no promise that a row exists. {@link #recordRequired} propagates the
 * failure instead, for callers whose whole point is the audit row — §5.2's manual admin attendance
 * override, which must never be a silent overwrite. Pick per call site; do not assume {@code
 * record} audited anything.
 *
 * <p>Concretely, a reader of this service's audit trail must treat the log as near-complete rather
 * than authoritative: a mutation can succeed without a row, and only the gap itself would reveal
 * it. That is the correct trade for CRUD whose database write has already committed.
 */
@Component
public class AuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

  private static final String SERVICE = "identity-service";

  private final AuditClient auditClient;

  /**
   * @param auditClient the shared client, or null when auditing is not configured in this
   *     environment (see {@link AuditConfig}) - a build agent has no AWS credentials
   */
  public AuditRecorder(AuditClient auditClient) {
    this.auditClient = auditClient;
  }

  /**
   * Records one mutation, best-effort: a failed write is logged and swallowed.
   *
   * <p><b>No guarantee is made that a row exists.</b> This is the right choice once the mutation
   * has already committed — reporting a failure would contradict the database — but it means a
   * caller that needs the audit row to be durable must use {@link #recordRequired} instead.
   *
   * @param action the verb, e.g. {@code employee.updated}
   * @param entity the entity type, which becomes the partition key
   * @param entityId the id within that entity type
   * @param details extra context; never credentials or a full entity dump
   */
  public void record(String action, String entity, String entityId, Map<String, String> details) {
    try {
      write(action, entity, entityId, details);
    } catch (AuditWriteException e) {
      // The mutation already committed, so failing the request here would be a lie about the
      // database while the audit row is genuinely missing. Log loudly and carry on.
      log.error("Could not audit {} on {} {}", action, entity, entityId, e);
    }
  }

  /**
   * Records one mutation and propagates a failure, for callers whose audit row is part of the
   * operation rather than an observation of it.
   *
   * <p>§5.2 requires a manual admin attendance override to write to the audit log and never be a
   * silent overwrite; an override that cannot be audited has not happened, so that path must fail
   * loudly. Same for any future admin or financial action. Do not use it for ordinary CRUD: the
   * mutation has already committed at that point, so throwing would tell the client its write was
   * rejected when the row is actually there.
   *
   * <p>A write is attempted before this can throw, so a caller that wants a durable row can rely on
   * it having been accepted by DynamoDB — not on it being indexed, since DynamoDB is eventually
   * consistent.
   *
   * @param action the verb, e.g. {@code attendance.overridden}
   * @param entity the entity type, which becomes the partition key
   * @param entityId the id within that entity type
   * @param details extra context; never credentials or a full entity dump
   * @throws AuditWriteException when auditing is not configured or the write did not succeed
   */
  public void recordRequired(
      String action, String entity, String entityId, Map<String, String> details) {
    write(action, entity, entityId, details);
  }

  /**
   * Builds the entry from the current principal and hands it to the client, letting any {@link
   * AuditWriteException} travel to the caller.
   */
  private void write(String action, String entity, String entityId, Map<String, String> details) {
    if (auditClient == null) {
      // Fail-open for record(), but a required audit must not vanish into a warning: an
      // unconfigured service is a deployment mistake, and silently skipping would hide it.
      throw new AuditWriteException(
          "Audit is not configured (no huvo.audit.table), so "
              + action
              + " on "
              + entity
              + " "
              + entityId
              + " cannot be recorded",
          null);
    }
    HuvoPrincipal principal = currentPrincipal();
    auditClient.record(
        AuditEntry.of(
            principal == null ? null : principal.userId(),
            principal == null ? null : principal.role(),
            action,
            entity,
            entityId,
            SERVICE,
            details));
  }

  /**
   * The caller for the in-flight request, or null for a system-initiated change (a bootstrap, a
   * scheduled job) or when no request is in flight at all. Null is a legitimate actor: "nobody,
   * because a job did it" is different from "we forgot to record who".
   */
  private static HuvoPrincipal currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    Object principal = authentication.getPrincipal();
    return principal instanceof HuvoPrincipal huvoPrincipal ? huvoPrincipal : null;
  }
}
