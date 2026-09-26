package com.huvo.attendance.day.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.engine.AttendanceStatus;

/**
 * One attendance day as returned by the API (Section 10: DTOs at the boundary, never the entity).
 *
 * @param id the row id
 * @param employeeId whose day this is
 * @param date the calendar day
 * @param status the outcome
 * @param firstLoginAt when they first logged in that day
 * @param autoMarked whether the lateness engine set this, rather than an admin
 * @param reasonNote the admin's explanation, when there was an override
 */
public record AttendanceDayResponse(
    Long id,
    Long employeeId,
    LocalDate date,
    AttendanceStatus status,
    LocalDateTime firstLoginAt,
    boolean autoMarked,
    String reasonNote) {

  public static AttendanceDayResponse from(AttendanceDay day) {
    return new AttendanceDayResponse(
        day.getId(),
        day.getEmployeeId(),
        day.getDate(),
        day.getStatus(),
        day.getFirstLoginAt(),
        day.isAutoMarked(),
        day.getReasonNote());
  }
}
