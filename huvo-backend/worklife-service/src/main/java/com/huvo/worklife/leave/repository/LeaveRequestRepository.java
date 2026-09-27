package com.huvo.worklife.leave.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.huvo.worklife.leave.LeaveStatus;
import com.huvo.worklife.leave.entity.LeaveRequest;

/** The leave_request table. */
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

  /** An employee's leave history, newest first. */
  List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);

  /** The approver's queue, oldest first so nothing starves behind newer requests. */
  List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveStatus status);

  /**
   * Existing requests whose range overlaps {@code from..to} for this employee.
   *
   * <p>Range-vs-range containment, {@code existing.from <= to AND existing.to >= from}, and both
   * endpoints inclusive - which is why a request starting on the day an existing one ends is a
   * conflict and one starting the day after is not. That edge is the whole reason this is written
   * out explicitly rather than as a derived query, where the inclusive/exclusive spelling is easy
   * to get subtly wrong in a way that still compiles.
   *
   * @param employeeId whose leave
   * @param statuses the statuses that count as occupying the days, normally PENDING and APPROVED
   * @param from the first day wanted, inclusive
   * @param to the last day wanted, inclusive
   * @return the overlapping requests, earliest first; empty when the range is free
   */
  @Query(
      """
      select r from LeaveRequest r
      where r.employeeId = :employeeId
        and r.status in :statuses
        and r.fromDate <= :to
        and r.toDate >= :from
      order by r.fromDate asc
      """)
  List<LeaveRequest> findOverlapping(
      @Param("employeeId") Long employeeId,
      @Param("statuses") List<LeaveStatus> statuses,
      @Param("from") java.time.LocalDate from,
      @Param("to") java.time.LocalDate to);
}
