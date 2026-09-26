package com.huvo.attendance.shift.entity;

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
 * Which shift an employee works, over a date range (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>{@code effectiveTo} being null means "current", so a closed assignment stays as a historical
 * fact rather than being deleted - which matters because past attendance_day rows are interpreted
 * against the roster that was in force on the day, not today's.
 */
@Entity
@Table(name = "roster")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Roster {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long employeeId;

  private Long shiftId;

  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private LocalDate effectiveTo;

  public Roster(Long employeeId, Long shiftId, LocalDate effectiveFrom, LocalDate effectiveTo) {
    this.employeeId = employeeId;
    this.shiftId = shiftId;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
  }

  /** Whether this assignment was in force on a given day. */
  public boolean isActiveOn(LocalDate day) {
    if (day.isBefore(effectiveFrom)) {
      return false;
    }
    return effectiveTo == null || !day.isAfter(effectiveTo);
  }
}
