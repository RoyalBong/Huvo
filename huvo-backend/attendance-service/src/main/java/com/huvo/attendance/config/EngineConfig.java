package com.huvo.attendance.config;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.huvo.attendance.engine.LatenessEngine;

/**
 * Beans for the attendance engine and the zone its wall-clock times are expressed in.
 *
 * <p>The engine is a plain object with no annotations: it is the product's highest-risk logic
 * (Section 1.1) and is instantiated here as a dependency rather than component-scanned, so nothing
 * can wire infrastructure into it by accident. Its unit tests construct it directly.
 */
@Configuration
public class EngineConfig {

  /**
   * The rules engine.
   *
   * @return a fresh instance; it holds no state
   */
  @Bean
  LatenessEngine latenessEngine() {
    return new LatenessEngine();
  }

  /**
   * The zone shift start times and attendance days are expressed in.
   *
   * <p>Externalised rather than assumed UTC, because §5.1 defines shifts as local wall-clock times
   * and a company in another region must not have its lateness measured against UTC.
   *
   * @param zoneId the configured zone
   * @return the zone
   */
  @Bean
  ZoneId attendanceZone(@Value("${huvo.attendance.zone:UTC}") String zoneId) {
    return ZoneId.of(zoneId);
  }
}
