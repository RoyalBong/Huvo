package com.huvo.attendance.login.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One successful login, as a raw fact (Huvo_Backend_Context.md Section 5.1).
 *
 * <p><b>Immutable and append-only.</b> There is deliberately no updated_at and no status column,
 * and the application layer never updates or deletes a row here. This table exists so the facts
 * survive any bug in the rules engine above it: the engine derives, this records. If the engine is
 * later found to have been wrong, the logins are still there to replay it against.
 *
 * <p>{@code sourceEventId} carries the producing event's id and is uniquely indexed, so a
 * redelivered {@code user.login.success} (Section 7 promises at-least-once) is rejected at the
 * database rather than double-counted.
 */
@Entity
@Table(name = "login_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long employeeId;

  /**
   * When the credentials were verified, as produced by identity-service - never this service's
   * clock, which would be later by the queue delay.
   */
  @Column(name = "login_timestamp", nullable = false)
  private LocalDateTime loginTimestamp;

  /** The client address, for the Section 5.3 audit columns. Metadata only, never enforced on. */
  @Column(name = "source_ip", length = 45)
  private String sourceIp;

  @Column(length = 100)
  private String device;

  /** The producing event's id, for at-least-once dedupe. Unique. */
  @Column(name = "source_event_id", length = 36, unique = true)
  private String sourceEventId;
}
