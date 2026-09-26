package com.huvo.attendance.messaging;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.attendance.day.entity.AttendanceDay;
import com.huvo.attendance.day.repository.AttendanceDayRepository;
import com.huvo.attendance.engine.AttendanceStatus;

import jakarta.annotation.PreDestroy;

/**
 * Consumes {@code leave.approved} from worklife-service and marks the covered days {@code ON_LEAVE}
 * (Huvo_Backend_Context.md Sections 5.3, 7).
 *
 * <p>This is what gives §5.3's "approved leave that day" rule something to read. Without it the
 * engine's leave branch could never fire from the event path, because nothing else writes an {@code
 * ON_LEAVE} row except an admin override.
 *
 * <p>Writing one row per covered day also matters beyond the skip: the streak derivation treats
 * {@code ON_LEAVE} as a day the employee was not expected in, so leave cannot break a late streak.
 * Marking only the first day would leave the rest looking like missed days.
 *
 * <p>Idempotent on {@code leaveId}, since Section 7 promises at-least-once. Re-running the same
 * approval is harmless anyway - the rows are written with a unique key per day - but the dedupe
 * avoids republishing nothing and re-deriving dates for no reason.
 */
@Component
public class LeaveApprovedListener {

  private static final Logger log = LoggerFactory.getLogger(LeaveApprovedListener.class);

  /**
   * A backstop on the range, so a malformed event cannot loop writing days. Three years of leave is
   * far beyond any real request; the bound is a guard on bad input, not a business rule.
   */
  private static final int MAX_LEAVE_DAYS = 1000;

  private final ObjectMapper objectMapper;
  private final AttendanceDayRepository days;

  private final Set<String> seenLeaveIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

  public LeaveApprovedListener(ObjectMapper objectMapper, AttendanceDayRepository days) {
    this.objectMapper = objectMapper;
    this.days = days;
  }

  @RabbitListener(queues = RabbitTopology.LEAVE_QUEUE)
  public void onLeaveApproved(String message) {
    Approval approval;
    try {
      approval = parse(message);
    } catch (Exception e) {
      // Log and drop: requeueing a malformed event would poison-loop the queue.
      log.error("Dropping unparseable leave.approved event", e);
      return;
    }
    if (approval.leaveId() == null || !seenLeaveIds.add(String.valueOf(approval.leaveId()))) {
      log.info("Skipping already-processed leave.approved {}", approval.leaveId());
      return;
    }
    markOnLeave(approval);
  }

  private record Approval(Long employeeId, LocalDate from, LocalDate to, Long leaveId) {}

  private Approval parse(String message) throws Exception {
    JsonNode envelope = objectMapper.readTree(message);
    JsonNode payload = envelope.get("payload");
    if (payload == null) {
      throw new IllegalArgumentException("leave.approved envelope carried no payload");
    }
    return new Approval(
        payload.get("employeeId").asLong(),
        LocalDate.parse(payload.get("from").asText()),
        LocalDate.parse(payload.get("to").asText()),
        payload.get("leaveId") == null ? null : payload.get("leaveId").asLong());
  }

  /** Marks every covered day, without overwriting a day that already has a decision. */
  @Transactional
  void markOnLeave(Approval approval) {
    LocalDate day = approval.from();
    int marked = 0;
    while (!day.isAfter(approval.to()) && marked < MAX_LEAVE_DAYS) {
      if (days.findByEmployeeIdAndDate(approval.employeeId(), day).isEmpty()) {
        AttendanceDay fresh = new AttendanceDay();
        fresh.setEmployeeId(approval.employeeId());
        fresh.setDate(day);
        fresh.setStatus(AttendanceStatus.ON_LEAVE);
        fresh.setReasonNote("Approved leave " + approval.leaveId());
        days.save(fresh);
      }
      day = day.plusDays(1);
      marked++;
    }
    log.info(
        "Marked {} day(s) ON_LEAVE for employee {} (leave {})",
        marked,
        approval.employeeId(),
        approval.leaveId());
  }

  @PreDestroy
  void clearSeenLeaveIds() {
    // Process-scoped only; marking the same days again is idempotent, so this is a fast path.
    seenLeaveIds.clear();
  }
}
