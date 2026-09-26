package com.huvo.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Huvo identity service - one deployable unit owning three domains (Huvo_Backend_Context.md
 * Sections 3.1 and 3.3):
 *
 * <ul>
 *   <li>{@code com.huvo.identity.auth} - login, JWT issue/refresh, RBAC source of truth
 *   <li>{@code com.huvo.identity.employee} - employee profiles
 *   <li>{@code com.huvo.identity.department} - departments/teams
 * </ul>
 *
 * Domains never share entities or repositories; cross-domain calls go through the other domain's
 * service interface, which keeps a future extraction into a standalone service a mechanical move
 * rather than a rewrite.
 */
@SpringBootApplication
public class IdentityServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(IdentityServiceApplication.class, args);
  }
}
