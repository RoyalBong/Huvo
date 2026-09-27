package com.huvo.worklife.task.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.huvo.worklife.task.TaskStatus;
import com.huvo.worklife.task.entity.Task;

/** The task table (Section 6.1). */
public interface TaskRepository extends JpaRepository<Task, Long> {

  /**
   * Open tasks with a deadline that has passed, which is exactly what the Section 6.2 sweep marks.
   *
   * <p>Filtering the status in SQL rather than loading everything open and filtering in Java
   * matters here: this runs every 15 minutes against a table that grows without bound, and the
   * composite index on {@code (status, deadline)} is built for precisely this predicate.
   *
   * @param now the sweep time
   * @return the tasks to mark overdue
   */
  @Query(
      """
      select t from Task t
      where t.status in :open
        and t.deadline is not null
        and t.deadline < :now
      """)
  List<Task> findOverdueCandidates(
      @Param("open") List<TaskStatus> open, @Param("now") LocalDateTime now);

  /**
   * An employee's tasks, newest first.
   *
   * <p>The id is a tiebreaker on purpose. Two tasks assigned in the same request burst share a
   * microsecond {@code created_at}, and an ORDER BY with no tiebreaker leaves their relative order
   * undefined - so the list could come back in a different order on two consecutive reads. The id
   * is monotonic, so it makes "newest first" actually mean it.
   */
  List<Task> findByEmployeeIdOrderByCreatedAtDescIdDesc(Long employeeId);

  /**
   * A manager's team's tasks, grouped-ready for the dashboard (Section 6.2).
   *
   * <p>Filtered by the departments the manager's JWT claim carries, so a manager's dashboard is one
   * query rather than a fan-out per department.
   *
   * @param departmentIds the manager's department scope
   * @return the tasks in those departments, newest first
   */
  List<Task> findByDepartmentIdInOrderByCreatedAtDescIdDesc(List<String> departmentIds);
}
