package com.huvo.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Huvo attendance service (Huvo_Backend_Context.md Sections 3.1, 5).
 *
 * <p>Standalone by design, and the only service that is: it owns the Section 5 lateness engine,
 * which the product's own ranking calls its highest-value logic. The isolation is deliberate load
 * shaping as much as architecture - everyone logs in around shift start, and this is the one
 * process whose traffic spike must not be able to affect the others.
 *
 * <p>Internal packages follow Section 3.3, with the rules engine deliberately its own boundary:
 *
 * <ul>
 *   <li>{@code com.huvo.attendance.engine} - the Section 5.2 rules, pure and isolated
 *   <li>{@code com.huvo.attendance.messaging} - consumes {@code user.login.success}
 *   <li>{@code com.huvo.attendance.login} / {@code .day} / {@code .shift} - schema-owned domains
 *   <li>{@code com.huvo.attendance.config} - security, Rabbit, audit wiring
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
public class AttendanceServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AttendanceServiceApplication.class, args);
  }
}
