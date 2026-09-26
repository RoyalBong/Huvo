package com.huvo.attendance.day.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.huvo.attendance.engine.AttendanceStatus;

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
 * The outcome for one employee on one day (Huvo_Backend_Context.md Section 5.1).
 *
 * <p>Unique on {@code (employee_id, date)} so two logins on the same day cannot produce two
 * competing outcomes - the second login reads the existing row and leaves it alone (Section 5.3).
 *
 * <p>{@code isAutoMarked} records that the lateness engine escalated this row rather than an admin
 * doing it, which is what a support query needs to tell the two apart. {@code reasonNote} carries
 * the human explanation on an override.
 */
@Entity
@Table(name = "attendance_day")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceDay {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long employeeId;

  private LocalDate date;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AttendanceStatus status;

  /**
   * The earliest login of the day (Section 5.2 step 3). Set once when the row is created and left
   * alone by later logins.
   */
  @Column(name = "first_login_at")
  private LocalDateTime firstLoginAt;

  @Column(name = "is_auto_marked", nullable = false)
  private boolean autoMarked;

  @Column(name = "reason_note", length = 255)
  private String reasonNote;

  public AttendanceDay(
      Long employeeId, LocalDate date, AttendanceStatus status, LocalDateTime firstLoginAt) {
    this.employeeId = employeeId;
    this.date = date;
    this.status = status;
    this.firstLoginAt = firstLoginAt;
    this.autoMarked = false;
  }
}
