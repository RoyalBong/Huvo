package com.huvo.attendance.day.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A manual attendance override (Huvo_Backend_Context.md Section 5.2 step 8).
 *
 * <p>{@code reasonNote} is required and size-bounded: an override that is not explained is exactly
 * the silent overwrite the rule forbids, so the note is part of the request rather than an optional
 * extra. The date comes from the path, so it cannot disagree with the URL.
 *
 * @param status the outcome to set, one of the attendance_day enum names
 * @param reasonNote why the override is being made, recorded in the audit trail
 */
public record AttendanceOverrideRequest(
    @NotBlank(message = "status must not be blank") String status,
    @NotBlank(message = "reasonNote must not be blank") @Size(max = 255) String reasonNote) {}
