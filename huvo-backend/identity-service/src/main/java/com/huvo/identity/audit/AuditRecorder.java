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
   * Records one mutation.
   *
   * @param action the verb, e.g. {@code employee.updated}
   * @param entity the entity type, which becomes the partition key
   * @param entityId the id within that entity type
   * @param details extra context; never credentials or a full entity dump
   */
  public void record(String action, String entity, String entityId, Map<String, String> details) {
    if (auditClient == null) {
      log.warn("Audit is not configured; skipping {} on {} {}", action, entity, entityId);
      return;
    }
    HuvoPrincipal principal = currentPrincipal();
    AuditEntry entry =
        AuditEntry.of(
            principal == null ? null : principal.userId(),
            principal == null ? null : principal.role(),
            action,
            entity,
            entityId,
            SERVICE,
            details);
    try {
      auditClient.record(entry);
    } catch (AuditWriteException e) {
      // The mutation already committed, so failing the request here would be a lie about the
      // database while the audit row is genuinely missing. Log loudly and carry on.
      log.error("Could not audit {} on {} {}", action, entity, entityId, e);
    }
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
