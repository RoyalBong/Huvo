package com.huvo.worklife.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.huvo.security.HuvoPrincipal;

/**
 * The Section 6.2 assignment rule: a manager assigns only within their {@code departmentIds}.
 *
 * <p>The interesting case is the negative one - the rule exists to stop a manager reaching outside
 * their team, so most of these assert refusal.
 */
class AssignmentScopeTest {

  private static HuvoPrincipal manager(Long... departmentIds) {
    return new HuvoPrincipal("1", "MANAGER", List.of(departmentIds), 10L);
  }

  @Test
  void aManagerMayAssignWithinTheirOwnDepartment() {
    assertThat(AssignmentScope.canAssignTo(manager(3L), "3")).isTrue();
  }

  @Test
  void aManagerMayAssignWithinAnyOfTheirDepartments() {
    HuvoPrincipal lead = manager(3L, 7L);

    assertThat(AssignmentScope.canAssignTo(lead, "3")).isTrue();
    assertThat(AssignmentScope.canAssignTo(lead, "7")).isTrue();
  }

  @Test
  void aManagerMayNotAssignOutsideTheirDepartments() {
    assertThat(AssignmentScope.canAssignTo(manager(3L), "9")).isFalse();
  }

  @Test
  void aManagerWithNoDepartmentsMayAssignToNoOne() {
    assertThat(AssignmentScope.canAssignTo(manager(), "3")).isFalse();
  }

  @Test
  void anAdminMayAssignOutsideAnyDepartment() {
    HuvoPrincipal admin = new HuvoPrincipal("1", "ADMIN", List.of(), 1L);

    assertThat(AssignmentScope.canAssignTo(admin, "999")).isTrue();
  }

  @Test
  void hrMayAssignOutsideAnyDepartment() {
    // HR owns headcount, so they are not scoped to one department.
    HuvoPrincipal hr = new HuvoPrincipal("1", "HR", List.of(), 1L);

    assertThat(AssignmentScope.canAssignTo(hr, "999")).isTrue();
  }

  @Test
  void anEmployeeWithNoDepartmentsMayNotAssignAtAll() {
    // Not a manager, and the empty claim gives them no scope to fall back on.
    HuvoPrincipal employee = new HuvoPrincipal("1", "EMPLOYEE", List.of(), 10L);

    assertThat(AssignmentScope.canAssignTo(employee, "3")).isFalse();
  }

  @Test
  void anUnknownTargetDepartmentIsRefusedRatherThanAllowed() {
    // The dangerous default: allowing an unknown department would let a manager sidestep the rule
    // by targeting an employee whose department is not known.
    assertThat(AssignmentScope.canAssignTo(manager(3L), null)).isFalse();
  }

  @Test
  void aDepartmentIdWithSurroundingWhitespaceStillMatches() {
    // identity-service's employee.department_id is free text for now, so a padded value must not
    // silently fail the scope check and refuse a legitimate assignment.
    assertThat(AssignmentScope.canAssignTo(manager(3L), " 3 ")).isTrue();
  }

  @Test
  void aDepartmentIdMustNotMatchByPrefix() {
    // Prevents a loose comparison treating "3" and "30" as the same department.
    assertThat(AssignmentScope.canAssignTo(manager(3L), "30")).isFalse();
  }

  @Test
  void aNullPrincipalIsRefused() {
    assertThat(AssignmentScope.canAssignTo(null, "3")).isFalse();
  }
}
