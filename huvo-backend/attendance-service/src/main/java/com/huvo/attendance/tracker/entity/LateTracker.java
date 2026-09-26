package com.huvo.attendance.tracker.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The week-scoped late-day count for one employee (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>There is deliberately no {@code consecutive_late_days_count} column. A streak is a property of
 * an employee's recent history, not of a week, and storing it in a week-keyed row reset it every
 * Monday - which made the three-consecutive-late-days rule unreachable, because the
 * two-late-days-in-a-week rule always fired first. The streak is derived from {@code
 * attendance_day} history instead; see {@code StreakDeriver}.
 */
@Entity
@Table(name = "late_tracker")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LateTracker {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long employeeId;

  /** Anchors the week. Derived from the company's working calendar, never a hardcoded Monday. */
  @Column(name = "week_start_date", nullable = false)
  private LocalDate weekStartDate;

  @Column(name = "late_days_count", nullable = false)
  private int lateDaysCount;

  public LateTracker(Long employeeId, LocalDate weekStartDate) {
    this.employeeId = employeeId;
    this.weekStartDate = weekStartDate;
    this.lateDaysCount = 0;
  }
}
