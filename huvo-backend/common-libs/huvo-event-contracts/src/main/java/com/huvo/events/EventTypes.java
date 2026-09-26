package com.huvo.events;

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
   * The literal ids only: who logged in, which employee profile, with what role. Never credentials
   * - a login event crosses a broker that other services read from.
   */
  public record LoginPayload(Long userId, Long employeeId, String role) {}
}
