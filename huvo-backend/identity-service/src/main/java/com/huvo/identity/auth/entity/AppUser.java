package com.huvo.identity.auth.entity;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Login identity for one Huvo user (Huvo_Backend_Context.md Sections 3.1, 3.3, 4.1). Owns the auth
 * sub-package only - employee/department domains never read this table directly; they see the
 * caller through the JWT claims ({@code sub}, {@code role}, {@code departmentIds}, {@code
 * employeeId}).
 *
 * <p>One row per user: exactly one Access Role ({@code ADMIN/HR/MANAGER/EMPLOYEE}), one optional
 * link to an employee profile, BCrypt-hashed password (never plaintext).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Login name. Case-insensitive uniqueness is enforced by normalising to lowercase on write. */
  @Column(nullable = false, unique = true, length = 190)
  private String username;

  /** BCrypt hash, never the raw password. */
  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  /** Access Role used by {@code @PreAuthorize} - exactly one per user (Section 4.1). */
  @Column(nullable = false, length = 20)
  private String role;

  /** Optional link to the employee profile this login belongs to (Section 4.2 claim). */
  @Column(name = "employee_id")
  private Long employeeId;

  /** Department scope copied into the JWT {@code departmentIds} claim (Section 4.2). */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "app_user_department", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "department_id")
  private Set<Long> departmentIds = new HashSet<>();

  /** Refresh-token rotation: only the hash of the current token is stored, never the token. */
  @Column(name = "refresh_token_hash", length = 64)
  private String refreshTokenHash;
}
