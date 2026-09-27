package com.huvo.notify.messaging;

import java.time.format.DateTimeFormatter;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.huvo.events.EventEnvelope;
import com.huvo.events.EventTypes;
import com.huvo.notify.service.NotificationService;
import com.huvo.notify.websocket.Channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turns consumed events into notifications (Section 7's event catalog).
 *
 * <p>Every listener follows the same shape, and it is deliberate: parse into {@code
 * EventEnvelope<T>} with {@code T} a shared payload record, then act. None of them touches {@code
 * JsonNode}.
 *
 * <p>That is the change from {@code LeaveApprovedListener}, which read its message field by field
 * and returned {@code 0} for anything missing. A producer that drifted - a renamed field, a bare
 * payload, a date as a timestamp array - left that listener silently producing wrong notifications
 * with no error anywhere. Here the same drift is a Jackson failure naming the field, and the event
 * is dropped and logged rather than acted on with nulls.
 *
 * <p>A null parse result means the event is skipped. Requeueing would poison-loop the queue, and
 * one malformed message must not stall every notification behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventNotificationListener {

  private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

  private final EnvelopeParser parser;
  private final NotificationService notifications;

  /** A login was late, Section 5.2 step 5b. Goes to the employee on the attendance channel. */
  @RabbitListener(queues = RabbitTopology.ATTENDANCE_QUEUE)
  public void onLateDetected(String message) {
    EventEnvelope<EventTypes.LateDetectedPayload> envelope =
        parser.parse(message, EventTypes.LateDetectedPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.ATTENDANCE,
        EventTypes.ATTENDANCE_LATE_DETECTED,
        "You were late",
        "You logged in "
            + payload.lateMinutes()
            + " minutes late on "
            + payload.date().format(DAY)
            + " (streak "
            + payload.streak()
            + ").");
  }

  /** A streak or frequency rule escalated the day to ABSENT, Section 5.2 step 6. */
  @RabbitListener(queues = RabbitTopology.ATTENDANCE_QUEUE)
  public void onAutoAbsent(String message) {
    EventEnvelope<EventTypes.AutoAbsentPayload> envelope =
        parser.parse(message, EventTypes.AutoAbsentPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.ATTENDANCE,
        EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED,
        "Marked absent",
        payload.date().format(DAY)
            + " was marked absent ("
            + payload.triggeredBy()
            + ", streak "
            + payload.streak()
            + ").");
  }

  /** A task was assigned, Section 6.2. */
  @RabbitListener(queues = RabbitTopology.TASK_QUEUE)
  public void onTaskAssigned(String message) {
    EventEnvelope<EventTypes.TaskAssignedPayload> envelope =
        parser.parse(message, EventTypes.TaskAssignedPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.TASKS,
        EventTypes.TASK_ASSIGNED,
        "New task: " + payload.title(),
        payload.deadline() == null ? "No deadline" : "Due " + payload.deadline().toLocalDate());
  }

  /** A task passed its deadline, Section 6.2. */
  @RabbitListener(queues = RabbitTopology.TASK_QUEUE)
  public void onTaskOverdue(String message) {
    EventEnvelope<EventTypes.TaskOverduePayload> envelope =
        parser.parse(message, EventTypes.TaskOverduePayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.TASKS,
        EventTypes.TASK_OVERDUE,
        "Task overdue: " + payload.title(),
        payload.deadline() == null
            ? "No deadline was set"
            : "Was due " + payload.deadline().toLocalDate());
  }

  /** A task came in after its deadline, Section 6.1's LATE_SUBMITTED flag. */
  @RabbitListener(queues = RabbitTopology.TASK_QUEUE)
  public void onTaskSubmittedLate(String message) {
    EventEnvelope<EventTypes.TaskSubmittedLatePayload> envelope =
        parser.parse(message, EventTypes.TaskSubmittedLatePayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.TASKS,
        EventTypes.TASK_SUBMITTED_LATE,
        "Submitted late: " + payload.title(),
        "Handed in on " + payload.submittedAt().toLocalDate() + ", after the deadline.");
  }

  /**
   * A leave request was filed.
   *
   * <p>Addressed to the applicant rather than the approver. The approver is reached by role, not by
   * id, and this service holds no copy of the org structure (Section 3.4) - resolving "who manages
   * whom" here would mean a cross-service call on every event. The applicant's own feed is the part
   * this service can do exactly, and it is the half that is currently missing.
   */
  @RabbitListener(queues = RabbitTopology.LEAVE_QUEUE)
  public void onLeaveApplied(String message) {
    EventEnvelope<EventTypes.LeaveAppliedPayload> envelope =
        parser.parse(message, EventTypes.LeaveAppliedPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.LEAVE,
        EventTypes.LEAVE_APPLIED,
        "Leave request sent",
        payload.from().format(DAY) + " to " + payload.to().format(DAY) + ", awaiting approval.");
  }

  /**
   * Leave was approved; attendance-service marks the covered days from the same event.
   *
   * <p>This service reads the same four fields as attendance, through a shared type rather than by
   * hand, so the two cannot disagree about which days are covered.
   */
  @RabbitListener(queues = RabbitTopology.LEAVE_QUEUE)
  public void onLeaveApproved(String message) {
    EventEnvelope<EventTypes.LeaveApprovedPayload> envelope =
        parser.parse(message, EventTypes.LeaveApprovedPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.LEAVE,
        EventTypes.LEAVE_APPROVED,
        "Leave approved",
        payload.from().format(DAY) + " to " + payload.to().format(DAY) + " is approved.");
  }

  /** Leave was rejected. Carries the reason, which is the point of telling the requester. */
  @RabbitListener(queues = RabbitTopology.LEAVE_QUEUE)
  public void onLeaveRejected(String message) {
    EventEnvelope<EventTypes.LeaveRejectedPayload> envelope =
        parser.parse(message, EventTypes.LeaveRejectedPayload.class);
    if (envelope == null) {
      return;
    }
    var payload = envelope.payload();
    notifications.notify(
        String.valueOf(payload.employeeId()),
        Channel.LEAVE,
        EventTypes.LEAVE_REJECTED,
        "Leave rejected",
        payload.from().format(DAY)
            + " to "
            + payload.to().format(DAY)
            + " was rejected: "
            + payload.reason());
  }
}
