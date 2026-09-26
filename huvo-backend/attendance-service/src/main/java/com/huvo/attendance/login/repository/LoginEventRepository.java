package com.huvo.attendance.login.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.huvo.attendance.login.entity.LoginEvent;

/**
 * The raw login fact table (Section 5.1).
 *
 * <p>Only ever inserted into and read from. No update or delete is offered here on purpose - making
 * the append-only contract visible in the type, not just in a comment.
 */
public interface LoginEventRepository extends JpaRepository<LoginEvent, Long> {

  /**
   * Whether this event has already been recorded, for at-least-once dedupe (Section 7).
   *
   * @param sourceEventId the producing event's id
   * @return true when a row with that id already exists
   */
  boolean existsBySourceEventId(String sourceEventId);

  Optional<LoginEvent> findBySourceEventId(String sourceEventId);
}
