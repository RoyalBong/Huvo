package com.huvo.events;

import static org.assertj.core.api.Assertions.assertThat;

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
  void everyDeclaredKeyIsUnique() {
    String[] all = {
      EventTypes.EMPLOYEE_CREATED,
      EventTypes.EMPLOYEE_UPDATED,
      EventTypes.EMPLOYEE_DELETED,
      EventTypes.DEPARTMENT_CREATED,
      EventTypes.DEPARTMENT_UPDATED,
      EventTypes.DEPARTMENT_DELETED,
      EventTypes.USER_LOGIN_SUCCESS
    };

    assertThat(all).doesNotHaveDuplicates();
  }

  @Test
  void theLoginPayloadCarriesIdsAndRoleOnly() {
    // The payload crosses a broker other services read from, so it must be ids - never
    // credentials. Naming the fields is the reminder that no secret may be added here.
    EventTypes.LoginPayload payload = new EventTypes.LoginPayload(1L, 2L, "MANAGER");

    assertThat(payload.userId()).isEqualTo(1L);
    assertThat(payload.employeeId()).isEqualTo(2L);
    assertThat(payload.role()).isEqualTo("MANAGER");
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
