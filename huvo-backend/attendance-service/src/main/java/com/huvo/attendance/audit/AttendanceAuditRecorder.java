package com.huvo.attendance.audit;

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
 * Writes audit rows for attendance actions, over the shared {@code huvo-audit-client}
 * (Huvo_Backend_Context.md Sections 3.4, 7).
 *
 * <p>The same two-policy shape as identity-service's recorder, for the same reason: whether an
 * audit write should fail the operation is a per-call decision. {@link #record} is best-effort for
 * the machine-driven paths, where the action is already committed. {@link #recordRequired}
 * propagates, and is what the admin override uses — §5.2 step 8 requires an override to write to
 * the audit log and never be a silent overwrite, so an override that cannot be audited must not be
 * reported as done.
 */
@Component
public class AttendanceAuditRecorder {

  private static final Logger log = LoggerFactory.getLogger(AttendanceAuditRecorder.class);

  private static final String SERVICE = "attendance-service";

  private final AuditClient auditClient;

  /**
   * @param auditClient the shared client, or null when auditing is not configured in this
   *     environment
   */
  public AttendanceAuditRecorder(AuditClient auditClient) {
    this.auditClient = auditClient;
  }

  /**
   * Records an action best-effort: a failed write is logged and swallowed because whatever it
   * describes has already been committed. No guarantee is made that a row exists.
   *
   * @param action the verb, e.g. {@code attendance.day.read}
   * @param entity the entity type, which becomes the partition key
   * @param entityId the id within that entity type
   * @param details extra context; never credentials or a full entity dump
   */
  public void record(String action, String entity, String entityId, Map<String, String> details) {
    try {
      write(action, entity, entityId, details);
    } catch (AuditWriteException e) {
      log.error("Could not audit {} on {} {}", action, entity, entityId, e);
    }
  }

  /**
   * Records an action and propagates a failure, for callers whose audit row is part of the
   * operation rather than an observation of it — the §5.2 step 8 manual override.
   *
   * @param action the verb
   * @param entity the entity type
   * @param entityId the id within that entity type
   * @param details extra context
   * @throws AuditWriteException when auditing is not configured or the write did not succeed
   */
  public void recordRequired(
      String action, String entity, String entityId, Map<String, String> details) {
    write(action, entity, entityId, details);
  }

  private void write(String action, String entity, String entityId, Map<String, String> details) {
    if (auditClient == null) {
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
   * The caller for the in-flight request, or null for a system-initiated change such as the
   * week-reset job. Null is a legitimate actor.
   */
  private static HuvoPrincipal currentPrincipal() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return null;
    }
    return authentication.getPrincipal() instanceof HuvoPrincipal huvo ? huvo : null;
  }
}
