package com.huvo.worklife.task.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.task.entity.Task;
import com.huvo.worklife.task.service.SubmissionUploadService;
import com.huvo.worklife.task.service.TaskService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * The task API (Huvo_Backend_Context.md Section 6).
 *
 * <p>Role guards live here; the rules that actually matter - department scope and "only the
 * assignee may act" - live in {@link TaskService}, because a {@code @PreAuthorize} is a guard and
 * not a rule.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

  private final TaskService tasks;
  private final SubmissionUploadService uploads;

  /**
   * Assigns a task to an employee (Section 6.2).
   *
   * @param principal the assigning manager
   * @param request the task to create
   * @return the created task
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public ResponseEntity<Task> assign(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @Valid @RequestBody AssignTaskRequest request) {
    Task created =
        tasks.assign(
            principal,
            request.title(),
            request.description(),
            request.employeeId(),
            request.departmentId(),
            request.deadline());
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  /** The caller's own tasks. */
  @GetMapping("/mine")
  public List<Task> mine(@AuthenticationPrincipal HuvoPrincipal principal) {
    return tasks.forEmployee(requireEmployee(principal));
  }

  /**
   * The manager dashboard (Section 6.2).
   *
   * @param principal the manager
   * @return tasks within the caller's department scope
   */
  @GetMapping("/dashboard")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public List<Task> dashboard(@AuthenticationPrincipal HuvoPrincipal principal) {
    return tasks.dashboard(principal);
  }

  /** Marks a task in progress (Section 6.1). */
  @PostMapping("/{taskId}/start")
  public Task start(@AuthenticationPrincipal HuvoPrincipal principal, @PathVariable Long taskId) {
    return tasks.start(principal, taskId);
  }

  /**
   * Mints a pre-signed S3 URL for a submission (Section 6.2).
   *
   * @param principal the assignee
   * @param taskId the task
   * @param request the file being uploaded
   * @return the URL and the key to record on submission
   */
  @PostMapping("/{taskId}/upload-url")
  public SubmissionUploadService.PresignedUpload uploadUrl(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @PathVariable Long taskId,
      @RequestBody UploadRequest request) {
    return uploads.presign(principal, taskId, request.fileName());
  }

  /**
   * Records a completed submission (Section 6.1).
   *
   * @param principal the assignee
   * @param taskId the task
   * @param request the S3 key the client uploaded to
   * @return the updated task
   */
  @PostMapping("/{taskId}/submit")
  public Task submit(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @PathVariable Long taskId,
      @RequestBody SubmitRequest request) {
    return tasks.submit(
        principal, taskId, request.objectKey(), request.originalName(), request.contentType());
  }

  /** Marks a submitted task complete (Section 6.1). */
  @PostMapping("/{taskId}/complete")
  @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN', 'HR')")
  public Task complete(
      @AuthenticationPrincipal HuvoPrincipal principal, @PathVariable Long taskId) {
    return tasks.complete(principal, taskId);
  }

  private Long requireEmployee(HuvoPrincipal principal) {
    if (principal.employeeId() == null) {
      throw new com.huvo.worklife.exception.BusinessRuleException(
          "Your account is not linked to an employee record");
    }
    return principal.employeeId();
  }

  /**
   * A task assignment (Section 6.2).
   *
   * @param title the task title
   * @param description optional detail
   * @param employeeId the assignee
   * @param departmentId the assignee's department, checked against the caller's scope
   * @param deadline when it is due; null for an open-ended task
   */
  public record AssignTaskRequest(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 5000) String description,
      @NotNull Long employeeId,
      @NotBlank String departmentId,
      OffsetDateTime deadline) {}

  /**
   * A request for a pre-signed upload URL (Section 6.2).
   *
   * @param fileName the client's filename, used only as a label
   */
  public record UploadRequest(@NotBlank String fileName) {}

  /**
   * A completed upload (Section 6.2).
   *
   * @param objectKey the key returned with the pre-signed URL
   * @param originalName the client's filename
   * @param contentType the content type
   */
  public record SubmitRequest(
      @NotBlank String objectKey, String originalName, String contentType) {}
}
