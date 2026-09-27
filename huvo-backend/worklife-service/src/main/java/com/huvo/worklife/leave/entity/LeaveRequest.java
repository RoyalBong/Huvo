package com.huvo.worklife.leave.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.huvo.worklife.leave.LeaveStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A leave request (Huvo_Backend_Context.md Section 7).
 *
 * <p>{@code fromDate} and {@code toDate} are <strong>inclusive</strong>, matching the {@code from}
 * and {@code to} on the {@code leave.approved} contract. That inclusivity is load-bearing on the
 * other side: attendance-service marks every covered day {@code ON_LEAVE}, and an off-by-one in the
 * producer's inclusive/exclusive choice would leave a day the employee was on leave looking absent
 * - and that would break their streak. Hence the field comment, because "inclusive" is the kind of
 * thing that gets "tidied up" later.
 */
@Entity
@Table(name = "leave_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveRequest {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "employee_id", nullable = false)
  private Long employeeId;

  /**
   * Free text for now, e.g. {@code ANNUAL}, {@code SICK}, {@code UNPAID}. No policy is specified.
   */
  @Column(name = "leave_type", nullable = false, length = 40)
  private String leaveType;

  /** First day requested, inclusive. */
  @Column(name = "from_date", nullable = false)
  private LocalDate fromDate;

  /** Last day requested, inclusive. */
  @Column(name = "to_date", nullable = false)
  private LocalDate toDate;

  @Column(length = 500)
  private String reason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeaveStatus status = LeaveStatus.PENDING;

  @Column(name = "decided_by", length = 64)
  private String decidedBy;

  @Column(name = "decision_note", length = 500)
  private String decisionNote;

  @Column(name = "decided_at")
  private LocalDateTime decidedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /** How many days this covers, counting both endpoints. */
  public long dayCount() {
    return java.time.temporal.ChronoUnit.DAYS.between(fromDate, toDate) + 1;
  }
}
