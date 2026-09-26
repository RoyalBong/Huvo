package com.huvo.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/**
 * The routing keys are a wire contract (Section 7). A typo here does not throw - on a topic
 * exchange an unmatched key is silently dropped - so the shape of these strings is pinned.
 */
class EventTypesTest {

  @Test
  void everyEmployeeKeySitsUnderTheEmployeePrefix() {
    assertThat(EventTypes.EMPLOYEE_CREATED).startsWith("employee.");
    assertThat(EventTypes.EMPLOYEE_UPDATED).startsWith("employee.");
    assertThat(EventTypes.EMPLOYEE_DELETED).startsWith("employee.");
  }

  @Test
  void everyDepartmentKeySitsUnderTheDepartmentPrefix() {
    assertThat(EventTypes.DEPARTMENT_CREATED).startsWith("department.");
    assertThat(EventTypes.DEPARTMENT_UPDATED).startsWith("department.");
    assertThat(EventTypes.DEPARTMENT_DELETED).startsWith("department.");
  }

  @Test
  void theWildcardPatternMatchesEveryKeyItOwns() {
    // "employee.#" must actually cover the three employee keys, or a consumer's binding
    // silently stops receiving events when a new action is added.
    for (String key :
        new String[] {
          EventTypes.EMPLOYEE_CREATED, EventTypes.EMPLOYEE_UPDATED, EventTypes.EMPLOYEE_DELETED
        }) {
      assertThat(matches(EventTypes.EMPLOYEE_ALL, key)).isTrue();
    }
    for (String key :
        new String[] {
          EventTypes.DEPARTMENT_CREATED,
          EventTypes.DEPARTMENT_UPDATED,
          EventTypes.DEPARTMENT_DELETED
        }) {
      assertThat(matches(EventTypes.DEPARTMENT_ALL, key)).isTrue();
    }
  }

  @Test
  void theEmployeePatternDoesNotLeakOtherDomains() {
    // A department consumer must never pick up employee traffic through a shared binding.
    assertThat(matches(EventTypes.EMPLOYEE_ALL, EventTypes.DEPARTMENT_CREATED)).isFalse();
    assertThat(matches(EventTypes.DEPARTMENT_ALL, EventTypes.EMPLOYEE_CREATED)).isFalse();
    assertThat(matches(EventTypes.EMPLOYEE_ALL, EventTypes.USER_LOGIN_SUCCESS)).isFalse();
  }

  @Test
  void everyAttendanceKeySitsUnderTheAttendancePrefix() {
    assertThat(EventTypes.ATTENDANCE_LATE_DETECTED).startsWith("attendance.");
    assertThat(EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED).startsWith("attendance.");
  }

  @Test
  void theAttendanceWildcardMatchesBothAttendanceKeys() {
    for (String key :
        new String[] {
          EventTypes.ATTENDANCE_LATE_DETECTED, EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED
        }) {
      assertThat(matches(EventTypes.ATTENDANCE_ALL, key)).isTrue();
    }
  }

  @Test
  void theAttendancePatternDoesNotLeakOtherDomains() {
    // notify-service binds attendance.#, so it must not receive login or employee traffic.
    assertThat(matches(EventTypes.ATTENDANCE_ALL, EventTypes.USER_LOGIN_SUCCESS)).isFalse();
    assertThat(matches(EventTypes.ATTENDANCE_ALL, EventTypes.EMPLOYEE_CREATED)).isFalse();
  }

  @Test
  void theLateDetectedPayloadCarriesTheMeasurementNotTheRecord() {
    EventTypes.LateDetectedPayload payload =
        new EventTypes.LateDetectedPayload(42L, java.time.LocalDate.of(2026, 9, 28), 47, 2, 1);

    assertThat(payload.employeeId()).isEqualTo(42L);
    assertThat(payload.lateMinutes()).isEqualTo(47);
    assertThat(payload.streak()).isEqualTo(2);
    assertThat(payload.lateDaysCount()).isEqualTo(1);
  }

  @Test
  void theStreakIsNamedAsTheTriggerWhenBothConditionsHold() {
    // Both rules can fire on the same day; the harder-to-reach one explains the escalation.
    assertThat(EventTypes.AutoAbsentPayload.triggerFor(3, 2))
        .isEqualTo(EventTypes.AutoAbsentPayload.TRIGGER_STREAK);
  }

  @Test
  void theWeeklyFrequencyIsNamedWhenTheStreakDidNotReachThree() {
    assertThat(EventTypes.AutoAbsentPayload.triggerFor(2, 2))
        .isEqualTo(EventTypes.AutoAbsentPayload.TRIGGER_WEEKLY_FREQUENCY);
  }

  @Test
  void theAutoAbsentPayloadCarriesBothCountsSoAReceiverCanRecheck() {
    EventTypes.AutoAbsentPayload payload =
        new EventTypes.AutoAbsentPayload(
            42L,
            java.time.LocalDate.of(2026, 9, 28),
            3,
            2,
            EventTypes.AutoAbsentPayload.triggerFor(3, 2));

    assertThat(payload.triggeredBy()).isEqualTo(EventTypes.AutoAbsentPayload.TRIGGER_STREAK);
    assertThat(payload.streak()).isEqualTo(3);
    assertThat(payload.lateDaysCount()).isEqualTo(2);
  }

  @Test
  void noAttendancePayloadCarriesCredentialsOrTokens() {
    var lateFields =
        java.util.Arrays.stream(EventTypes.LateDetectedPayload.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
    var absentFields =
        java.util.Arrays.stream(EventTypes.AutoAbsentPayload.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertThat(lateFields)
        .containsExactlyInAnyOrder("employeeId", "date", "lateMinutes", "streak", "lateDaysCount");
    assertThat(absentFields)
        .containsExactlyInAnyOrder("employeeId", "date", "streak", "lateDaysCount", "triggeredBy");
  }

  @Test
  void everyDeclaredKeyIsUnique() {
    String[] all = {
      EventTypes.EMPLOYEE_CREATED,
      EventTypes.EMPLOYEE_UPDATED,
      EventTypes.EMPLOYEE_DELETED,
      EventTypes.DEPARTMENT_CREATED,
      EventTypes.DEPARTMENT_UPDATED,
      EventTypes.DEPARTMENT_DELETED,
      EventTypes.USER_LOGIN_SUCCESS,
      EventTypes.ATTENDANCE_LATE_DETECTED,
      EventTypes.ATTENDANCE_AUTO_ABSENT_TRIGGERED
    };

    assertThat(all).doesNotHaveDuplicates();
  }

  @Test
  void theLoginPayloadCarriesIdsAndRoleOnly() {
    // The payload crosses a broker other services read from, so it must be ids and metadata -
    // never credentials. Naming the fields is the reminder that no secret may be added here.
    EventTypes.LoginPayload payload =
        new EventTypes.LoginPayload(
            1L, 2L, "MANAGER", OffsetDateTime.parse("2026-09-27T09:15:00+05:30"), "10.0.0.7");

    assertThat(payload.userId()).isEqualTo(1L);
    assertThat(payload.employeeId()).isEqualTo(2L);
    assertThat(payload.role()).isEqualTo("MANAGER");
  }

  /**
   * Section 5.2 computes the lateness delta from {@code login_timestamp}, not from the envelope's
   * {@code occurredAt} - which is stamped later, during token issuance and serialisation. Against a
   * 15-minute grace period that lag would flip borderline logins, so the two must be
   * distinguishable fields and the earlier one must be the one the engine reads.
   */
  @Test
  void theLoginTimestampIsSeparateFromTheEnvelopeTimestamp() {
    // Five minutes ago, so the assertion is about ordering rather than about the wall clock.
    OffsetDateTime verifiedAt = OffsetDateTime.now().minusMinutes(5);

    EventTypes.LoginPayload payload =
        new EventTypes.LoginPayload(1L, 2L, "EMPLOYEE", verifiedAt, "10.0.0.7");
    EventEnvelope<EventTypes.LoginPayload> envelope =
        EventEnvelope.of(EventTypes.USER_LOGIN_SUCCESS, "identity-service", payload);

    // The payload keeps the instant the password verified, unchanged and not overwritten by the
    // envelope...
    assertThat(payload.loginTimestamp()).isEqualTo(verifiedAt);
    // ...while the envelope records when the event was built, which is strictly later. Deriving
    // one from the other is the bug this guards: against a 15-minute grace period, a systematic
    // lag would flip on-time logins to late.
    assertThat(envelope.occurredAt()).isAfter(verifiedAt);
  }

  @Test
  void theLoginPayloadSurvivesAnUnlinkedAccountAndAnUnknownClient() {
    // A bootstrap or service account has no employee record, and a non-HTTP caller has no IP.
    // Both are legitimate; neither should stop the engine from consuming the login.
    EventTypes.LoginPayload payload =
        new EventTypes.LoginPayload(1L, null, "ADMIN", OffsetDateTime.now(), null);

    assertThat(payload.employeeId()).isNull();
    assertThat(payload.sourceIp()).isNull();
    assertThat(payload.loginTimestamp()).isNotNull();
  }

  @Test
  void theLoginPayloadNeverCarriesCredentialsOrTokens() {
    EventTypes.LoginPayload payload =
        new EventTypes.LoginPayload(
            1L, 2L, "EMPLOYEE", OffsetDateTime.parse("2026-09-27T09:15:00+05:30"), "10.0.0.7");

    // A guard on the contract: a password or token field would end up readable by every
    // service bound to the exchange, so this fails loudly if one is ever added.
    var fieldNames =
        java.util.Arrays.stream(EventTypes.LoginPayload.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();

    assertThat(fieldNames)
        .containsExactlyInAnyOrder("userId", "employeeId", "role", "loginTimestamp", "sourceIp");
    assertThat(fieldNames)
        .noneMatch(
            name ->
                name.toLowerCase(java.util.Locale.ROOT).contains("password")
                    || name.toLowerCase(java.util.Locale.ROOT).contains("token")
                    || name.toLowerCase(java.util.Locale.ROOT).contains("secret"));
  }

  /** Minimal AMQP topic matching: "#" is zero or more words, "*" is exactly one. */
  private static boolean matches(String pattern, String key) {
    String[] patternWords = pattern.split("\\.");
    String[] keyWords = key.split("\\.");
    int i = 0;
    for (int p = 0; p < patternWords.length; p++) {
      String word = patternWords[p];
      if ("#".equals(word)) {
        return p == patternWords.length - 1;
      }
      if (i >= keyWords.length || !("*".equals(word) || word.equals(keyWords[i]))) {
        return false;
      }
      i++;
    }
    return i == keyWords.length;
  }
}
