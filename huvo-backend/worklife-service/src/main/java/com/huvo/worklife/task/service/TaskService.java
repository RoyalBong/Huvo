package com.huvo.worklife.task.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.audit.AuditClient;
import com.huvo.audit.AuditEntry;
import com.huvo.events.EventTypes;
import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.exception.NotFoundException;
import com.huvo.worklife.messaging.WorklifeEventPublisher;
import com.huvo.worklife.task.AssignmentScope;
import com.huvo.worklife.task.TaskStatus;
import com.huvo.worklife.task.TaskTransitions;
import com.huvo.worklife.task.entity.Task;
import com.huvo.worklife.task.entity.TaskSubmission;
import com.huvo.worklife.task.repository.TaskRepository;
import com.huvo.worklife.task.repository.TaskSubmissionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Task assignment and progression (Huvo_Backend_Context.md Sections 6.1, 6.2).
 *
 * <p>Two rules are enforced here rather than in the controller, because a controller annotation is
 * not a rule:
 *
 * <ul>
 *   <li>a manager may only assign within their departments ({@link AssignmentScope})
 *   <li>only the assignee may act on a task
 * </ul>
 *
 * <p>Both would be bypassed by calling the endpoint with someone else's id otherwise.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

  private static final String SERVICE = "worklife-service";

  private final TaskRepository tasks;
  private final TaskSubmissionRepository submissions;
  private final WorklifeEventPublisher publisher;
  private final AuditClient audit;

  /**
   * Assigns a task, enforcing the Section 6.2 department rule.
   *
   * @param principal the assigning manager
   * @param title the task title
   * @param description optional detail
   * @param employeeId the assignee
   * @param targetDepartmentId the assignee's department, for the scope check
   * @param deadline when it is due, or null for an open-ended task
   * @return the saved task
   */
  @Transactional
  public Task assign(
      HuvoPrincipal principal,
      String title,
      String description,
      Long employeeId,
      String targetDepartmentId,
      OffsetDateTime deadline) {
    if (!AssignmentScope.canAssignTo(principal, targetDepartmentId)) {
      // Refused before anything is written, and the refusal itself is recorded: someone probing the
      // department boundary is exactly what an audit trail exists to show.
      recordQuietly(principal, "task.assignment-refused", employeeId, targetDepartmentId);
      throw new BusinessRuleException(
          "You may only assign tasks to employees in your own departments");
    }

    Task task = new Task(title, employeeId, deadline, OffsetDateTime.now());
    task.setDescription(description);
    task.setAssignedByUser(principal.userId());
    task.setDepartmentId(targetDepartmentId);
    Task saved = tasks.save(task);

    record(principal, "task.assigned", saved.getId(), targetDepartmentId);
    publisher.publishTaskAssigned(
        new EventTypes.TaskAssignedPayload(
            saved.getId(), employeeId, principal.userId(), title, deadline));
    return saved;
  }

  /**
   * Moves a task to {@code IN_PROGRESS} (Section 6.1).
   *
   * @param principal the assignee
   * @param taskId the task
   * @return the updated task
   */
  @Transactional
  public Task start(HuvoPrincipal principal, Long taskId) {
    Task task = requireAssignee(principal, taskId);
    task.setStatus(TaskTransitions.start(task.getStatus()));
    return tasks.save(task);
  }

  /**
   * Submits a task against an uploaded object, recording whether it was late (Section 6.1).
   *
   * <p>The file itself is not handled here: Section 6.2 has the client upload straight to S3 with a
   * pre-signed URL, and this only records the resulting key.
   *
   * @param principal the assignee
   * @param taskId the task
   * @param objectKey the S3 key the client uploaded to
   * @param originalName the client's filename, for display only
   * @param contentType the content type
   * @return the updated task
   */
  @Transactional
  public Task submit(
      HuvoPrincipal principal,
      Long taskId,
      String objectKey,
      String originalName,
      String contentType) {
    Task task = requireAssignee(principal, taskId);
    OffsetDateTime now = OffsetDateTime.now();

    TaskStatus newStatus = TaskTransitions.submit(task.getStatus(), deadlineOf(task), now);
    task.setStatus(newStatus);
    task.setSubmittedAt(now.toLocalDateTime());
    Task saved = tasks.save(task);

    TaskSubmission submission = new TaskSubmission();
    submission.setTaskId(taskId);
    submission.setEmployeeId(task.getEmployeeId());
    submission.setObjectKey(objectKey);
    submission.setOriginalName(originalName);
    submission.setContentType(contentType);
    submission.setSubmittedAt(now.toLocalDateTime());
    submissions.save(submission);

    if (newStatus == TaskStatus.LATE_SUBMITTED) {
      // The submission itself is a success. This only tells the manager it came in late, so it is
      // published rather than treated as a failure.
      publisher.publishTaskSubmittedLate(
          new EventTypes.TaskSubmittedLatePayload(
              taskId, task.getEmployeeId(), task.getTitle(), deadlineOf(task), now));
    }
    return saved;
  }

  /**
   * Marks a submitted task complete (Section 6.1).
   *
   * @param principal the manager
   * @param taskId the task
   * @return the updated task
   */
  @Transactional
  public Task complete(HuvoPrincipal principal, Long taskId) {
    Task task =
        tasks.findById(taskId).orElseThrow(() -> new NotFoundException("No task " + taskId));
    task.setStatus(TaskTransitions.complete(task.getStatus()));
    task.setCompletedAt(OffsetDateTime.now().toLocalDateTime());
    Task saved = tasks.save(task);
    record(principal, "task.completed", taskId, saved.getStatus().name());
    return saved;
  }

  /** One employee's tasks, newest first. */
  @Transactional(readOnly = true)
  public List<Task> forEmployee(Long employeeId) {
    return tasks.findByEmployeeIdOrderByCreatedAtDesc(employeeId);
  }

  /**
   * A manager's dashboard (Section 6.2).
   *
   * <p>Scoped to the departments in the caller's claim, so one query serves every manager and the
   * department grouping is left to the database rather than a fan-out per department.
   *
   * @param principal the manager
   * @return the tasks within their departments
   */
  @Transactional(readOnly = true)
  public List<Task> dashboard(HuvoPrincipal principal) {
    if (AssignmentScope.isUnrestricted(principal)) {
      // ADMIN and HR are not department-scoped. An empty IN list is an SQL error rather than
      // "everything", so they take the unscoped path deliberately rather than by accident.
      return tasks.findAll();
    }
    List<String> scope = principal.departmentIds().stream().map(String::valueOf).toList();
    if (scope.isEmpty()) {
      // A manager with no departments gets an empty dashboard rather than everyone's.
      return List.of();
    }
    return tasks.findByDepartmentIdInOrderByCreatedAtDesc(scope);
  }

  /**
   * The Section 6.2 deadline sweep.
   *
   * <p>Idempotent: a task already {@code OVERDUE} is excluded by the query, so a second run in the
   * same window marks nothing and publishes nothing.
   *
   * @return how many tasks were marked
   */
  @Transactional
  public int markOverdue() {
    LocalDateTime now = OffsetDateTime.now().toLocalDateTime();
    List<TaskStatus> open = List.of(TaskStatus.ASSIGNED, TaskStatus.IN_PROGRESS);
    List<Task> candidates = tasks.findOverdueCandidates(open, now);

    for (Task task : candidates) {
      task.setStatus(TaskStatus.OVERDUE);
      tasks.save(task);
      publisher.publishTaskOverdue(
          new EventTypes.TaskOverduePayload(
              task.getId(), task.getEmployeeId(), task.getTitle(), deadlineOf(task)));
    }
    if (!candidates.isEmpty()) {
      // System-initiated, so there is no actor to record.
      recordQuietly(null, "task.overdue-sweep", candidates.size(), null);
    }
    return candidates.size();
  }

  private OffsetDateTime deadlineOf(Task task) {
    LocalDateTime deadline = task.getDeadline();
    return deadline == null ? null : deadline.atOffset(OffsetDateTime.now().getOffset());
  }

  /**
   * The task, only if this caller is the assignee.
   *
   * <p>The check is here rather than in the controller because the assignee is the only thing
   * standing between one employee submitting another employee's work.
   */
  private Task requireAssignee(HuvoPrincipal principal, Long taskId) {
    Task task =
        tasks.findById(taskId).orElseThrow(() -> new NotFoundException("No task " + taskId));
    Long employeeId = principal.employeeId();
    if (employeeId == null || !employeeId.equals(task.getEmployeeId())) {
      throw new BusinessRuleException("This task is assigned to someone else");
    }
    return task;
  }

  /** Guaranteed audit: a missing client refuses the operation. */
  private void record(HuvoPrincipal principal, String action, Long entityId, String detail) {
    if (audit == null) {
      throw new IllegalStateException(
          "No audit client is configured, so this change cannot be recorded. Set AUDIT_TABLE.");
    }
    audit.record(
        AuditEntry.of(
            principal.userId(),
            principal.role(),
            action,
            "task",
            String.valueOf(entityId),
            SERVICE,
            Map.of("detail", detail == null ? "" : detail)));
  }

  /**
   * Best-effort audit: recorded when a client exists, skipped when it does not.
   *
   * <p>Used where failing to record must not also fail the operation - a refusal, or a scheduled
   * sweep. The distinction from {@link #record} is deliberate and should not be blurred: it is the
   * difference between "we could not audit this" and "this never happened".
   */
  private void recordQuietly(
      HuvoPrincipal principal, String action, Object entityId, String detail) {
    if (audit == null) {
      return;
    }
    audit.recordQuietly(
        AuditEntry.of(
            principal == null ? null : principal.userId(),
            principal == null ? null : principal.role(),
            action,
            "task",
            String.valueOf(entityId),
            SERVICE,
            Map.of("detail", detail == null ? "" : detail)));
  }
}
