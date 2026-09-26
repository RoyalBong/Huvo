package com.huvo.attendance.engine;

import java.time.LocalTime;

/**
 * A shift's clock window and its lateness allowance (Huvo_Backend_Context.md Section 5.1).
 *
 * @param id the shift id
 * @param name the display name
 * @param startTime when the shift starts, local wall-clock
 * @param endTime when it ends, local wall-clock
 * @param graceMinutes how many minutes after {@code startTime} a login still counts as on time
 */
public record ShiftWindow(
    Long id, String name, LocalTime startTime, LocalTime endTime, int graceMinutes) {

  public ShiftWindow {
    if (graceMinutes < 0) {
      throw new IllegalArgumentException("graceMinutes cannot be negative, was " + graceMinutes);
    }
  }
}
