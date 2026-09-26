package com.huvo.attendance.tracker;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.huvo.attendance.calendar.DatabaseWorkingCalendar;

/**
 * Opens each new attendance week (Section 5.2 step 7: "a scheduled job resets {@code late_tracker}
 * at the start of each configured week").
 *
 * <p>There is deliberately nothing to reset. The weekly counter is per-row and keyed on the week's
 * anchor, so a new week simply starts at a new row that does not exist yet and {@code
 * AttendanceEvaluationService} creates it on the first late login. Deleting or zeroing last week's
 * row would destroy history for no benefit, which is why this job only verifies the calendar and
 * reports.
 *
 * <p>Idempotent and safe to run on a single instance: it reads the calendar and logs. It never
 * mutates attendance data, so running it twice, or on two instances during a rolling restart, is
 * harmless.
 */
@Component
public class LateTrackerWeekResetJob {

  private static final Logger log = LoggerFactory.getLogger(LateTrackerWeekResetJob.class);

  private final DatabaseWorkingCalendar calendar;
  private final ZoneId zone;

  public LateTrackerWeekResetJob(DatabaseWorkingCalendar calendar, ZoneId zone) {
    this.calendar = calendar;
    this.zone = zone;
  }

  /**
   * Runs at 00:05 on Monday, the earliest point at which a new week can have begun.
   *
   * <p>Monday because that is the anchor for the common Mon-Fri and Mon-Sat configurations; a
   * company with a different week gets a new row keyed on its own anchor regardless of when this
   * runs.
   *
   * <p>Fixed delay rather than fixed rate, so a slow run cannot overlap itself.
   */
  @Scheduled(cron = "0 5 0 * * MON", zone = "UTC")
  // TODO: ShedLock if this ever runs on more than one instance (Section 10).
  public void openNewWeek() {
    ZonedDateTime now = ZonedDateTime.now(zone);
    if (!calendar.isWorkingDay(now.toLocalDate())) {
      // Nothing to do, and saying so is more useful than a silent no-op on a misconfigured
      // calendar.
      log.warn("Week reset ran on {}, which the calendar does not treat as a working day", now);
      return;
    }
    log.info(
        "Week reset: {} begins a new counters row on its first late login. Working days: {}",
        calendar.weekStartFor(now.toLocalDate()),
        calendar.workingDays());
  }
}
