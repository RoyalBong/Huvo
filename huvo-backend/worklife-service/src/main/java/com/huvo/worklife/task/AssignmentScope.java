package com.huvo.worklife.task;

import java.util.Set;

import com.huvo.security.HuvoPrincipal;

/**
 * The assignment rule from Section 6.2: a manager can assign tasks only to employees within their
 * {@code departmentIds}.
 *
 * <p>Checked against the JWT claim the manager already carries, with the same shape of fresh check
 * the section permits for high-stakes actions. Two consequences worth stating plainly:
 *
 * <ul>
 *   <li>A claim is only as fresh as the access token, so a department change takes effect when the
 *       manager's token is refreshed (15 minutes, Section 4.2) rather than instantly.
 *   <li>{@code departmentIds} comes from the manager's own record. It says which departments they
 *       manage, not which employees report to them, so this is a scope check and not a
 *       reporting-line check. A per-employee reporting line would need the fresh identity-service
 *       call the section mentions.
 * </ul>
 *
 * <p>Pure and static so the rule is unit-testable without a token or a database.
 */
public final class AssignmentScope {

  /** Roles that may assign work to anyone, bypassing the department check. */
  private static final Set<String> UNRESTRICTED_ROLES = Set.of("ADMIN", "HR");

  private AssignmentScope() {}

  /**
   * Whether this caller is exempt from the department check entirely.
   *
   * <p>Separated from {@link #canAssignTo} because the dashboard needs the same exemption with a
   * different consequence: a manager with no departments gets an empty list, while ADMIN and HR get
   * everything. Deriving that from a "no target department" call would conflate the two.
   *
   * @param principal the caller
   * @return true when the department scope does not apply
   */
  public static boolean isUnrestricted(HuvoPrincipal principal) {
    return principal != null && UNRESTRICTED_ROLES.contains(principal.role());
  }

  /**
   * Whether this manager may assign a task to an employee in a given department.
   *
   * @param principal the assigning caller
   * @param targetDepartmentId the assignee's department, or null when unknown
   * @return true when the assignment is allowed
   */
  public static boolean canAssignTo(HuvoPrincipal principal, String targetDepartmentId) {
    if (principal == null) {
      return false;
    }
    if (UNRESTRICTED_ROLES.contains(principal.role())) {
      return true;
    }
    if (targetDepartmentId == null) {
      // No department to check against, and a manager must not be able to sidestep the rule by
      // assigning to an employee whose department is unknown.
      return false;
    }
    // departmentId is a String here (employee.department_id is VARCHAR pending the org-chart
    // relation) while the claim holds Longs, so the comparison is numeric. A String.equals against
    // a Long is always false, which would refuse every legitimate assignment.
    String target = targetDepartmentId.trim();
    for (Long departmentId : principal.departmentIds()) {
      // A null claim entry must not match a null-ish id either.
      if (departmentId != null && String.valueOf(departmentId).equals(target)) {
        return true;
      }
    }
    return false;
  }
}
