package com.huvo.worklife.task.entity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.huvo.worklife.task.TaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A unit of work (Huvo_Backend_Context.md Section 6.1).
 *
 * <p>{@code deadline} is nullable on purpose. An open-ended task has no deadline, and defaulting
 * one would make every such task inventably overdue ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â the sweep and the
 * late-submission check both treat null as "never late".
 */
@Entity
@Table(name = "task")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

  /**
   * The single frame every task timestamp is stored and compared in.
   *
   * <p>The column is a naive {@code LocalDateTime} because MySQL's {@code DATETIME} carries no
   * offset. That is only safe if every write and every comparison uses the same zone, so they all
   * use this one.
   */
  public static final java.time.ZoneOffset STORAGE_ZONE = ZoneOffset.UTC;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String description;

  @Column(name = "employee_id", nullable = false)
  private Long employeeId;

  /** The assigner's user id, so a notification can name a person rather than an id. */
  @Column(name = "assigned_by_user", length = 64)
  private String assignedByUser;

  /**
   * Copied from the assignee's departments at assignment time (Section 6.2).
   *
   * <p>Denormalised deliberately: the assignment rule needs the department scope to be checkable
   * without a cross-service call to identity-service on the write path, and Section 3.4 forbids
   * reading identity-service's schema. It is a snapshot, so a later department change does not
   * retroactively change who may see an old task.
   */
  @Column(name = "department_id", length = 64)
  private String departmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TaskStatus status = TaskStatus.ASSIGNED;

  @Column(name = "deadline")
  private LocalDateTime deadline;

  @Column(name = "submitted_at")
  private LocalDateTime submittedAt;

  @Column(name = "completed_at")
  private LocalDateTime completedAt;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  /**
   * @param deadline when the task is due, or null for an open-ended one. Stored as UTC.
   * @param createdAt when it was assigned. Stored as UTC.
   */
  public Task(
      String title,
      Long employeeId,
      java.time.OffsetDateTime deadline,
      java.time.OffsetDateTime createdAt) {
    this.title = title;
    this.employeeId = employeeId;
    // Normalised to UTC, not stored as the wall-clock time it arrived in. MySQL's DATETIME carries
    // no offset, so a bare toLocalDateTime() would keep "17:00" from a +05:30 manager and the sweep
    // would then compare it against a UTC now - marking the task overdue 5h30m early. Same class of
    // bug as measuring attendance against UTC (Section 5.1), and the fix is the same: one absolute
    // frame for writes and comparisons alike.
    this.deadline =
        deadline == null ? null : deadline.withOffsetSameInstant(STORAGE_ZONE).toLocalDateTime();
    this.createdAt = createdAt.withOffsetSameInstant(STORAGE_ZONE).toLocalDateTime();
    this.status = TaskStatus.ASSIGNED;
  }
}
