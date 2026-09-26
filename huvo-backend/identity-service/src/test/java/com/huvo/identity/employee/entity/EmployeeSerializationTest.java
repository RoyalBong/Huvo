package com.huvo.identity.employee.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Regression guard for the API contract of {@link Employee}.
 *
 * <p>The entity used to expose only {@code private} accessors, which Lombok will not replace, so
 * Jackson could neither write {@code name}/{@code departmentId}/{@code salary} to responses nor
 * read them from request bodies. These tests fail if that regresses.
 */
class EmployeeSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void serializesEveryFieldInTheApiContract() throws Exception {
    Employee employee = new Employee(7L, "Asha Rao", "3", 75000.0);

    String json = objectMapper.writeValueAsString(employee);

    assertThat(json)
        .contains("\"id\":7")
        .contains("\"name\":\"Asha Rao\"")
        .contains("\"departmentId\":\"3\"")
        .contains("\"salary\":75000.0");
  }

  @Test
  void deserializesEveryFieldFromARequestBody() throws Exception {
    String json = "{\"id\":7,\"name\":\"Asha Rao\",\"departmentId\":\"3\",\"salary\":75000.0}";

    Employee employee = objectMapper.readValue(json, Employee.class);

    assertThat(employee.getId()).isEqualTo(7L);
    assertThat(employee.getName()).isEqualTo("Asha Rao");
    assertThat(employee.getDepartmentId()).isEqualTo("3");
    assertThat(employee.getSalary()).isEqualTo(75000.0);
  }
}
