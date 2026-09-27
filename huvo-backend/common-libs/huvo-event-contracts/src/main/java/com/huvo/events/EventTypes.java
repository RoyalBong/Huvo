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
   * The exchange identity-service publishes on. attendance-service declares it too, so it can bind
   * its own queue whether or not identity-service has started yet - a consumer should not depend on
   * a producer's startup order.
   */
  public static final String IDENTITY_EXCHANGE = "identity.exchange";

  /**
   * The exchange attendance-service publishes lateness outcomes on.
   *
   * <p>Lives here rather than only in attendance-service's own topology because notify-service
   * binds a queue to it. An exchange name is wire format, and a name one service keeps to itself is
   * a name the next consumer will retype - which is how a binding silently stops matching.
   */
  public static final String ATTENDANCE_EXCHANGE = "attendance.exchange";

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

  // --- attendance domain: attendance-service -> notify, payroll (future) ---

  public static final String ATTENDANCE_LATE_DETECTED = "attendance.late.detected";
  public static final String ATTENDANCE_AUTO_ABSENT_TRIGGERED = "attendance.autoAbsent.triggered";

  /** Binding pattern for the full attendance stream. */
  public static final String ATTENDANCE_ALL = "attendance.#";

  /**
   * A login was late against the employee's shift (Section 5.2 step 5b). notify-service sends the
   * late-warning email immediately, unbatched.
   *
   * <p>Ids and the measurement, never a copy of the attendance row: a consumer needs to know who
   * and by how much.
   *
   * @param employeeId whose day was late
   * @param date the calendar day, from the login timestamp
   * @param lateMinutes minutes past the grace period
   * @param streak consecutive late scheduled working days ending on this one
   * @param lateDaysCount late days so far this week
   */
  public record LateDetectedPayload(
      Long employeeId, java.time.LocalDate date, int lateMinutes, int streak, int lateDaysCount) {}

  /**
   * The streak or weekly-frequency rule escalated the day to ABSENT (Section 5.2 step 6).
   * notify-service emails the employee and the manager/HR.
   *
   * @param employeeId whose day was escalated
   * @param date the calendar day
   * @param streak the streak that triggered, or 0 when the weekly frequency rule did
   * @param lateDaysCount the weekly count that triggered, or 0 when the streak did
   * @param triggeredBy which rule fired
   */
  public record AutoAbsentPayload(
      Long employeeId,
      java.time.LocalDate date,
      int streak,
      int lateDaysCount,
      String triggeredBy) {

    /** Three consecutive late scheduled working days. */
    public static final String TRIGGER_STREAK = "STREAK";

    /** Two or more late days in the same week. */
    public static final String TRIGGER_WEEKLY_FREQUENCY = "WEEKLY_FREQUENCY";

    /**
     * Which rule fired when both conditions hold on the same day.
     *
     * <p>Both can be true at once, and naming the one that actually explains the escalation is more
     * useful to a support query than "both". The streak is reported because it is the harder
     * condition to reach, so it is the more surprising explanation.
     *
     * @param streak the streak reached
     * @param lateDaysCount the weekly count reached
     * @return the trigger to publish
     */
    public static String triggerFor(int streak, int lateDaysCount) {
      return streak >= 3 ? TRIGGER_STREAK : TRIGGER_WEEKLY_FREQUENCY;
    }
  }

  // --- leave domain: worklife-service -> notify, attendance ---

  public static final String LEAVE_APPLIED = "leave.applied";
  public static final String LEAVE_APPROVED = "leave.approved";
  public static final String LEAVE_REJECTED = "leave.rejected";

  /** Binding pattern for the full leave stream. */
  public static final String LEAVE_ALL = "leave.#";

  /** The exchange worklife-service publishes leave on. */
  public static final String LEAVE_EXCHANGE = "leave.exchange";

  /**
   * A leave request was submitted (Section 7). notify-service tells the approver.
   *
   * <p>Same shape as {@link LeaveApprovedPayload} and deliberately so: an approver and the
   * attendance consumer want the same facts, and one shape means a future addition is a coordinated
   * change rather than two drifting ones.
   *
   * @param employeeId whose leave it is
   * @param from the first day requested, inclusive
   * @param to the last day requested, inclusive
   * @param leaveId the request id
   */
  public record LeaveAppliedPayload(
      Long employeeId, java.time.LocalDate from, java.time.LocalDate to, Long leaveId) {}

  /**
   * Leave was approved (Section 7). attendance-service consumes this to mark the covered days
   * {@code ON_LEAVE}, so Section 5.3's "approved leave that day" rule skips the lateness logic.
   *
   * <p>A range rather than a single date, because leave spans days. The consumer writes one {@code
   * attendance_day} per covered day, which is also what the streak derivation needs: a day the
   * employee was not expected in must not break a late streak.
   *
   * @param employeeId whose leave it is
   * @param from the first day covered, inclusive
   * @param to the last day covered, inclusive
   * @param leaveId the leave request id, for traceability
   */
  public record LeaveApprovedPayload(
      Long employeeId, java.time.LocalDate from, java.time.LocalDate to, Long leaveId) {}

  /**
   * A leave request was rejected. notify-service tells the requester.
   *
   * <p>Carries the same range as the approval, so a consumer can dismiss the right days, plus the
   * approver's reason - which is the one thing the requester needs and the approver wants recorded.
   *
   * @param employeeId whose leave it is
   * @param from the first day requested, inclusive
   * @param to the last day requested, inclusive
   * @param leaveId the request id
   * @param reason why it was rejected
   */
  public record LeaveRejectedPayload(
      Long employeeId,
      java.time.LocalDate from,
      java.time.LocalDate to,
      Long leaveId,
      String reason) {}

  // --- task domain: worklife-service -> notify ---

  public static final String TASK_ASSIGNED = "task.assigned";
  public static final String TASK_OVERDUE = "task.overdue";
  public static final String TASK_SUBMITTED_LATE = "task.submitted.late";

  /** Binding pattern for the full task stream. */
  public static final String TASK_ALL = "task.#";

  /** The exchange worklife-service publishes tasks on. */
  public static final String TASK_EXCHANGE = "task.exchange";

  /**
   * A task was assigned to an employee (Section 6.2). notify-service tells them.
   *
   * @param taskId the task
   * @param employeeId the assignee
   * @param assignedByUserId the assigner's user id, so the notification can name a person
   * @param title the task title, for a readable notification
   * @param deadline when it is due, or null for an open-ended task
   */
  public record TaskAssignedPayload(
      Long taskId,
      Long employeeId,
      String assignedByUserId,
      String title,
      java.time.OffsetDateTime deadline) {}

  /**
   * A task passed its deadline without being submitted (Section 6.2). notify-service tells the
   * assignee and their manager.
   *
   * @param taskId the task
   * @param employeeId the assignee
   * @param title the task title
   * @param deadline when it was due
   */
  public record TaskOverduePayload(
      Long taskId, Long employeeId, String title, java.time.OffsetDateTime deadline) {}

  /**
   * A task was submitted after its deadline (Section 6.1's {@code LATE_SUBMITTED} flag). The
   * submission itself is a normal success - this only tells the manager it came in late.
   *
   * @param taskId the task
   * @param employeeId the assignee
   * @param title the task title
   * @param deadline when it was due
   * @param submittedAt when it was actually submitted
   */
  public record TaskSubmittedLatePayload(
      Long taskId,
      Long employeeId,
      String title,
      java.time.OffsetDateTime deadline,
      java.time.OffsetDateTime submittedAt) {}
}
