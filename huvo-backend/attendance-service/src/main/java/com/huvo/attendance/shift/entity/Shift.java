package com.huvo.attendance.shift.entity;

import java.time.LocalTime;

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
 * A named block of hours with its own lateness allowance (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>{@code startTime} and {@code endTime} are local wall-clock on purpose: "the 09:00 shift" is a
 * clock concept, and storing a zone here would imply a date the shift does not have. A night shift
 * starting at 22:00 is therefore a start time before its end time, and the engine measures it
 * against the login's own calendar day.
 */
@Entity
@Table(name = "shift")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Shift {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100, unique = true)
  private String name;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  /**
   * How many minutes after {@code startTime} a login still counts as on time. Per shift rather than
   * global, so a late-shift team can have a different rule.
   */
  @Column(name = "grace_minutes", nullable = false)
  private int graceMinutes = 15;

  public Shift(String name, LocalTime startTime, LocalTime endTime, int graceMinutes) {
    this.name = name;
    this.startTime = startTime;
    this.endTime = endTime;
    this.graceMinutes = graceMinutes;
  }
}
