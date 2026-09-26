package com.huvo.attendance.day.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.attendance.day.dto.AttendanceDayResponse;
import com.huvo.attendance.day.dto.AttendanceOverrideRequest;
import com.huvo.attendance.day.service.AttendanceDayService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The attendance dashboard's read API, plus the manual override.
 *
 * <p>Reads are open to any authenticated role: an attendance record is not sensitive personal data
 * the way salary is, and managers, HR and the employee all need to see it. The override is ADMIN/HR
 * only — it is the one write that bypasses the rules engine, so it gets the tighter gate.
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceDayController {

  private final AttendanceDayService service;

  /**
   * One employee's days in a range.
   *
   * @param employeeId whose days
   * @param from inclusive start
   * @param to inclusive end
   * @return the days, newest first
   */
  @GetMapping("/{employeeId}")
  public List<AttendanceDayResponse> list(
      @PathVariable Long employeeId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.findBetween(employeeId, from, to);
  }

  /** One employee's single day. */
  @GetMapping("/{employeeId}/{date}")
  public AttendanceDayResponse getOne(
      @PathVariable Long employeeId,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.findOne(employeeId, date);
  }

  /**
   * The manual override (Section 5.2 step 8). ADMIN or HR, and guaranteed-audited: the service will
   * refuse the change rather than apply one it cannot record.
   */
  @PutMapping("/{employeeId}/{date}/override")
  @PreAuthorize("hasAnyRole('ADMIN','HR')")
  public ResponseEntity<AttendanceDayResponse> override(
      @PathVariable Long employeeId,
      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @Valid @RequestBody AttendanceOverrideRequest request) {
    return ResponseEntity.ok(service.override(employeeId, date, request));
  }
}
