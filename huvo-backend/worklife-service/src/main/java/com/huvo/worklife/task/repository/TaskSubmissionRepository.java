package com.huvo.worklife.task.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.worklife.task.entity.TaskSubmission;

/** The task_submission table (Section 6.2). */
public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

  /** Every file handed in against a task, oldest first. */
  List<TaskSubmission> findByTaskIdOrderBySubmittedAtAsc(Long taskId);
}
