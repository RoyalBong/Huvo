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
   * Whether the employee already has leave of this status covering a given day.
   *
   * <p>Stops a second request overlapping one already approved, which would otherwise publish two
   * {@code leave.approved} events covering the same day and have attendance mark it {@code
   * ON_LEAVE} twice for one day of leave.
   *
   * <p>Written as an explicit overlap predicate rather than a derived query: {@code from <= :date
   * AND to >= :date} is range containment, and the derived-query spelling of it is easy to get
   * subtly wrong in a way that compiles.
   *
   * @param employeeId whose leave
   * @param status the status to consider
   * @param date the day in question
   * @return true when an existing request covers that day
   */
  @Query(
      """
      select case when count(r) > 0 then true else false end
      from LeaveRequest r
      where r.employeeId = :employeeId
        and r.status = :status
        and r.fromDate <= :date
        and r.toDate >= :date
      """)
  boolean existsCovering(
      @Param("employeeId") Long employeeId,
      @Param("status") LeaveStatus status,
      @Param("date") java.time.LocalDate date);
}
