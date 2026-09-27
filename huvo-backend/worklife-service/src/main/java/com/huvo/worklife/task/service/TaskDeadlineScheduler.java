package com.huvo.worklife.task.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The Section 6.2 deadline sweep.
 *
 * <p>Runs every 15 minutes, which is the granularity of the notification rather than of
 * correctness: a task is late whenever it is late, and this only decides when anyone is told.
 * Running it more often would publish duplicate overdue notifications for the same task.
 *
 * <p>Idempotent. The query excludes tasks already marked {@code OVERDUE}, so a restart, a double
 * schedule, or two instances running the sweep concurrently each produce at most one notification
 * per task. That is why no distributed lock is needed yet.
 *
 * <p>{@code // TODO: ShedLock if scaled horizontally} - safe on one instance, which is the current
 * deployment (Section 8.3). The exclusion is the only thing making that true, so it must survive
 * edits to the query.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskDeadlineScheduler {

  private final TaskService tasks;

  /** Marks open tasks whose deadline has passed, and publishes {@code task.overdue}. */
  @Scheduled(cron = "${huvo.task.overdue-sweep-cron:0 */15 * * * *}")
  public void markOverdueTasks() {
    try {
      int marked = tasks.markOverdue();
      if (marked > 0) {
        log.info("Deadline sweep marked {} task(s) overdue", marked);
      }
    } catch (RuntimeException e) {
      // A scheduler thread that throws is cancelled by default, so the next 15-minute window would
      // silently never run. Catching here keeps the schedule alive through a transient failure.
      log.error("Deadline sweep failed; the next window will retry", e);
    }
  }
}
