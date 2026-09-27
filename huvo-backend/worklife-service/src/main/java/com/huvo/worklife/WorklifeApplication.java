package com.huvo.worklife;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * worklife-service (Huvo_Backend_Context.md Sections 3.1, 3.2, 12).
 *
 * <p>Owns the task, leave and onboarding domains.
 *
 * <p>{@link EnableScheduling} is here rather than per-class because the service has exactly two
 * scheduled jobs - the Section 6.2 task-deadline sweep and the document-expiry check - and both are
 * infrastructure this service owns wholesale (Section 12: they already share scheduler infra).
 */
@SpringBootApplication
@EnableScheduling
public class WorklifeApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorklifeApplication.class, args);
  }
}
