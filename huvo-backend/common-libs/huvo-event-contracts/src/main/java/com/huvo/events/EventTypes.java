package com.huvo.events;

import java.time.OffsetDateTime;

/**
 * Routing keys published on a bounded context's exchange (Huvo_Backend_Context.md Section 7).
 *
 * <p>Consumers bind patterns ({@code employee.#}, {@code department.#}), never exact keys, so a new
 * action is added to the exchange without touching any existing binding. Centralising the strings
 * here is what stops a producer's typo from becoming a silently unroutable message: an unmatched
 * key on a topic exchange is dropped, not queued.
 */
public final class EventTypes {

  private EventTypes() {}

  // --- employee domain: identity-service -> attendance, worklife, payroll ---

  public static final String EMPLOYEE_CREATED = "employee.created";
  public static final String EMPLOYEE_UPDATED = "employee.updated";
  public static final String EMPLOYEE_DELETED = "employee.deleted";

  /** Binding pattern for the full employee stream. */
  public static final String EMPLOYEE_ALL = "employee.#";

  // --- department domain: identity-service -> attendance, worklife, payroll ---

  public static final String DEPARTMENT_CREATED = "department.created";
  public static final String DEPARTMENT_UPDATED = "department.updated";
  public static final String DEPARTMENT_DELETED = "department.deleted";

  /** Binding pattern for the full department stream. */
  public static final String DEPARTMENT_ALL = "department.#";

  // --- auth domain: identity-service -> attendance (drives the lateness engine) ---

  public static final String USER_LOGIN_SUCCESS = "user.login.success";

  /**
   * The fact that a login succeeded, for the attendance lateness engine (Section 5.2).
   *
   * <p>Ids and timestamps only. Never credentials, never a token: this crosses a broker that other
   * services read from, and the tokens are not needed by any consumer.
   *
   * <p>{@code loginTimestamp} is captured the moment the password verifies, <em>not</em> the
   * envelope's {@code occurredAt}, which is stamped later during token issuance and serialisation.
   * Section 5.2 computes {@code delta = login_timestamp - shift.start_time} against a 15-minute
   * grace period, so a systematic lag would make every borderline login late. {@code sourceIp} is
   * the client address, which Section 5.3 records for audit only and does not enforce on.
   *
   * <p>{@code role} is carried for the audit trail but is deliberately not used by the engine —
   * Section 5.2 applies the same rules to every role from CEO to Associate Analyst.
   *
   * @param userId the login's user id
   * @param employeeId the linked employee record, or null when the account is not linked to one yet
   * @param role the access role, for the audit record only
   * @param loginTimestamp when the credentials were validated, never when the event was published
   * @param sourceIp the client IP as seen by the edge proxy, or null when unavailable
   */
  public record LoginPayload(
      Long userId, Long employeeId, String role, OffsetDateTime loginTimestamp, String sourceIp) {}
}
