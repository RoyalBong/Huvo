package com.huvo.attendance.day.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.attendance.audit.AttendanceAuditRecorder;
import com.huvo.attendance.day.dto.AttendanceDayResponse;
import com.huvo.attendance.day.dto.AttendanceOverrideRequest;
import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.day.repository.AttendanceDayRepository;
import com.huvo.attendance.engine.AttendanceStatus;
import com.huvo.attendance.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Reads of the attendance record, and the one path that writes outside the rules engine: the manual
 * admin override (Section 5.2 step 8).
 */
@Service
@RequiredArgsConstructor
public class AttendanceDayService {

  private static final String ENTITY = "attendance_day";
  private static final String OVERRIDE_ACTION = "attendance.day.overridden";

  private final AttendanceDayRepository days;
  private final AttendanceAuditRecorder audit;

  /**
   * One employee's days in a date range, newest first.
   *
   * @param employeeId whose days
   * @param from inclusive
   * @param to inclusive
   * @return the days in range
   */
  @Transactional(readOnly = true)
  public List<AttendanceDayResponse> findBetween(Long employeeId, LocalDate from, LocalDate to) {
    return days.findByEmployeeIdAndDateBetweenOrderByDateDesc(employeeId, from, to).stream()
        .map(AttendanceDayResponse::from)
        .toList();
  }

  /**
   * One employee's single day.
   *
   * @param employeeId whose day
   * @param date the day
   * @return the day
   * @throws ResourceNotFoundException when they have no record for that date
   */
  @Transactional(readOnly = true)
  public AttendanceDayResponse findOne(Long employeeId, LocalDate date) {
    return days.findByEmployeeIdAndDate(employeeId, date)
        .map(AttendanceDayResponse::from)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No attendance record for employee " + employeeId + " on " + date));
  }

  /**
   * The manual override (Section 5.2 step 8).
   *
   * <p><b>Guaranteed audit.</b> The rule says an override must write to {@code huvo_audit_log} and
   * must never be a silent overwrite, so this calls {@link AttendanceAuditRecorder#recordRequired}
   * and lets a failed audit write propagate - the {@code GlobalExceptionHandler} turns that into a
   * 503 and the row is left untouched. An override that cannot be audited has not happened.
   *
   * <p>{@code autoMarked} is cleared, so an overridden day is visibly a human decision rather than
   * an engine one, and the previous status is recorded in the audit detail.
   *
   * @param employeeId whose day
   * @param date the day
   * @param request the new status and the required reason
   * @return the updated day
   */
  @Transactional
  public AttendanceDayResponse override(
      Long employeeId, LocalDate date, AttendanceOverrideRequest request) {
    AttendanceStatus status = parseStatus(request.status());
    AttendanceDay day =
        days.findByEmployeeIdAndDate(employeeId, date)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "No attendance record for employee " + employeeId + " on " + date));

    String previous = day.getStatus().name();
    day.setStatus(status);
    day.setAutoMarked(false);
    day.setReasonNote(request.reasonNote());
    days.save(day);

    // The audit write is what makes this an override rather than an edit. It runs after the save
    // but inside the same transaction, so a failed audit write rolls the status back rather than
    // leaving a silently changed row.
    Map<String, String> details = new LinkedHashMap<>();
    details.put("date", date.toString());
    details.put("previousStatus", previous);
    details.put("newStatus", status.name());
    details.put("reasonNote", request.reasonNote());
    audit.recordRequired(OVERRIDE_ACTION, ENTITY, String.valueOf(employeeId), details);

    return AttendanceDayResponse.from(day);
  }

  /** Rejects an unknown status name rather than defaulting to something. */
  private static AttendanceStatus parseStatus(String raw) {
    try {
      return AttendanceStatus.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown attendance status: "
              + raw
              + ". Expected one of PRESENT, LATE, ABSENT, ON_LEAVE, HOLIDAY");
    }
  }
}
