package com.huvo.attendance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The login timestamp is the engine's only clock (Huvo_Backend_Context.md Section 5.2 step 4:
 * {@code delta = login_timestamp - shift.start_time}). These tests exist because the failure mode
 * is invisible: an engine that quietly reached for {@code now()} would pass almost every scenario
 * and would only be wrong when the queue backed up or the replayed event was old.
 */
class LatenessEngineTimestampTest {

  private static final ZoneOffset ZONE = ZoneOffset.UTC;
  private static final ZoneOffset IST = ZoneOffset.ofHoursMinutes(5, 30);

  private static ShiftWindow morningShift() {
    return new ShiftWindow(1L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), 15);
  }

  private static LatenessDecision at(
      OffsetDateTime loginAt, ShiftWindow shift, ZoneId zone, WorkingCalendar calendar) {
    return new LatenessEngine()
        .evaluate(
            42L,
            loginAt,
            Optional.ofNullable(shift),
            LateCounters.zero(),
            0,
            false,
            false,
            false,
            calendar,
            zone);
  }

  @Test
  void theDeltaIsComputedFromTheLoginTimestampNotALocalClock() {
    // Two logins two minutes apart, one on time and one late. If the engine used now(), the
    // second could not be late while the first was on time.
    LatenessDecision onTime =
        at(
            OffsetDateTime.of(2026, 9, 28, 9, 14, 0, 0, ZONE),
            morningShift(),
            ZONE,
            FixedWorkingCalendar.mondayToFriday());
    LatenessDecision late =
        at(
            OffsetDateTime.of(2026, 9, 28, 9, 16, 0, 0, ZONE),
            morningShift(),
            ZONE,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(onTime.outcome()).isEqualTo(AttendanceStatus.PRESENT);
    assertThat(late.outcome()).isEqualTo(AttendanceStatus.LATE);
  }

  @Test
  void aStaleReplayedEventIsJudgedByWhenItHappenedNotWhenItWasDelivered() {
    // The defining test for at-least-once delivery: a login from 09:16 that arrives much later is
    // still late by 16 minutes. A clock-reading engine would call it very late, or not late at all
    // depending on the hour, and both answers would be wrong.
    LatenessDecision decision =
        at(
            OffsetDateTime.of(2026, 9, 28, 9, 16, 0, 0, ZONE),
            morningShift(),
            ZONE,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.lateMinutes()).isEqualTo(16);
  }

  @Test
  void aLoginInAnotherOffsetIsJudgedAgainstTheShiftInTheConfiguredZone() {
    // 04:30 UTC is 10:00 in UTC+05:30, so it is an hour late against a 09:00 IST shift.
    LatenessDecision decision =
        at(
            OffsetDateTime.of(2026, 9, 28, 4, 30, 0, 0, ZONE),
            morningShift(),
            IST,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.lateMinutes()).isEqualTo(60);
  }

  @Test
  void theDecisionDateIsTakenFromTheLoginTimestampInTheConfiguredZone() {
    // 23:30 UTC on Sunday is already Monday 05:00 in IST, so the day must roll over - otherwise a
    // Monday late login would be filed against Sunday, a non-working day.
    LatenessDecision decision =
        at(
            OffsetDateTime.of(2026, 9, 27, 23, 30, 0, 0, ZONE),
            null,
            IST,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.date()).isEqualTo(LocalDate.of(2026, 9, 28));
  }

  @Test
  void aNightShiftStartIsMeasuredAgainstTheLoginsOwnDay() {
    // A 22:00 night shift: a 00:30 login the next morning is before that evening's start, so it
    // must not be treated as hours late against the wrong day.
    ShiftWindow night = new ShiftWindow(2L, "Night", LocalTime.of(22, 0), LocalTime.of(6, 0), 15);

    LatenessDecision decision =
        at(
            OffsetDateTime.of(2026, 9, 29, 0, 30, 0, 0, ZONE),
            night,
            ZONE,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.PRESENT);
  }

  @Test
  void aLateNightShiftLoginIsLateAgainstItsOwnEvening() {
    // The mirror of the previous test: 23:30 against a 22:00 start is 90 minutes late, and the
    // grace period applies to that evening rather than to the following morning.
    ShiftWindow night = new ShiftWindow(2L, "Night", LocalTime.of(22, 0), LocalTime.of(6, 0), 15);

    LatenessDecision decision =
        at(
            OffsetDateTime.of(2026, 9, 28, 23, 30, 0, 0, ZONE),
            night,
            ZONE,
            FixedWorkingCalendar.mondayToFriday());

    assertThat(decision.outcome()).isEqualTo(AttendanceStatus.LATE);
    assertThat(decision.lateMinutes()).isEqualTo(90);
  }
}
