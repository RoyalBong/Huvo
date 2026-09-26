package com.huvo.identity.department.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Regression guard for the API contract of {@link Department}.
 *
 * <p>The entity used to expose only {@code private} accessors, which Lombok will not replace, so
 * Jackson serialized every department as {@code {}} and could not read {@code name}/{@code
 * location} from request bodies. These tests fail if that regresses.
 */
class DepartmentSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesEveryFieldInTheApiContract() throws Exception {
    Department department = new Department(3L, "Engineering", "Bengaluru");

    String json = objectMapper.writeValueAsString(department);

    assertThat(json)
        .contains("\"id\":3")
        .contains("\"name\":\"Engineering\"")
        .contains("\"location\":\"Bengaluru\"");
  }

  @Test
  void deserializesEveryFieldFromARequestBody() throws Exception {
    String json = "{\"id\":3,\"name\":\"Engineering\",\"location\":\"Bengaluru\"}";

    Department department = objectMapper.readValue(json, Department.class);

    assertThat(department.getId()).isEqualTo(3L);
    assertThat(department.getName()).isEqualTo("Engineering");
    assertThat(department.getLocation()).isEqualTo("Bengaluru");
  }
}
