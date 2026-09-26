package com.huvo.identity.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.employee.messaging.EmployeeEventPublisher;
import com.huvo.identity.employee.repository.EmployeeRepository;
import com.huvo.identity.support.BearerTokens;
import com.huvo.security.HuvoTokenService;

/**
 * Contract tests for the employee API: DTOs in and out, validation, the single documented error
 * body (Huvo Backend_Context.md §10) and the 404 paths.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private EmployeeRepository repository;

  @Autowired private HuvoTokenService tokens;

  /** The broker is an external boundary and is not needed for these assertions. */
  @MockitoBean private EmployeeEventPublisher eventPublisher;

  /**
   * These endpoints stopped being anonymous when the filter chain landed, so every request now
   * carries a real signed ADMIN token rather than an unauthenticated call.
   */
  private RequestPostProcessor auth;

  @BeforeEach
  void cleanDatabase() {
    repository.deleteAll();
    auth = BearerTokens.forAdmin(tokens);
  }

  @Test
  void createsAnEmployeeWith201AndReturnsTheRepresentation() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"departmentId\":\"3\",\"salary\":75000.0}")
                .with(auth))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Asha Rao"))
        .andExpect(jsonPath("$.departmentId").value("3"))
        .andExpect(jsonPath("$.salary").value(75000.0));

    Employee saved = repository.findAll().get(0);
    verify(eventPublisher).publishEmployeeCreated(saved.getId());
  }

  @Test
  void rejectsABlankNameWithTheDocumentedErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"   \",\"salary\":10.0}")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request validation failed"))
        .andExpect(jsonPath("$.path").value("/api/employees"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.details[0]").value("name: name must not be blank"));
  }

  @Test
  void rejectsANegativeSalary() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"salary\":-1.0}")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.details[0]").value("salary: salary must not be negative"));
  }

  @Test
  void returns404ForAnUnknownEmployee() throws Exception {
    mockMvc
        .perform(get("/api/employees/4242").with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value(containsString("4242")))
        .andExpect(jsonPath("$.path").value("/api/employees/4242"))
        .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void updateReplacesTheEmployeeRepresentation() throws Exception {
    Employee saved = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            put("/api/employees/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao Jr\",\"departmentId\":\"4\",\"salary\":80000.0}")
                .with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().intValue()))
        .andExpect(jsonPath("$.name").value("Asha Rao Jr"))
        .andExpect(jsonPath("$.departmentId").value("4"))
        .andExpect(jsonPath("$.salary").value(80000.0));

    verify(eventPublisher).publishEmployeeUpdated(saved.getId());
  }

  @Test
  void updateOnAnUnknownEmployeeIs404() throws Exception {
    mockMvc
        .perform(
            put("/api/employees/4242")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"salary\":1.0}")
                .with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void deleteRemovesTheEmployeeAndIs404OnTheSecondAttempt() throws Exception {
    Employee saved = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(delete("/api/employees/" + saved.getId()).with(auth))
        .andExpect(status().isNoContent());

    assertThat(repository.existsById(saved.getId())).isFalse();
    verify(eventPublisher).publishEmployeeDeleted(saved.getId());

    mockMvc
        .perform(delete("/api/employees/" + saved.getId()).with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  /**
   * Guards the tricky part of the advice: Spring MVC's own exceptions must keep their correct
   * status code while still being rendered in the documented error body.
   */
  @Test
  void frameworkErrorsAlsoUseTheDocumentedErrorBody() throws Exception {
    mockMvc
        .perform(patch("/api/employees/1").with(auth))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
        .andExpect(jsonPath("$.path").value("/api/employees/1"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  /**
   * Headcount is an HR duty (Section 4.1). A MANAGER runs a team's attendance and tasks; they do
   * not edit or delete employee records, so the token must be refused before the handler runs.
   */
  @Test
  void refusesToCreateAnEmployeeWithAManagerToken() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"departmentId\":\"3\",\"salary\":75000.0}")
                .with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        .andExpect(jsonPath("$.path").value("/api/employees"));

    assertThat(repository.count()).isZero();
    verify(eventPublisher, never()).publishEmployeeCreated(anyLong());
  }

  @Test
  void refusesToUpdateAnEmployeeWithAManagerToken() throws Exception {
    Employee saved = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            put("/api/employees/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao Jr\",\"salary\":80000.0}")
                .with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    // The record is untouched, not merely reported as refused.
    assertThat(repository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Asha Rao");
    verify(eventPublisher, never()).publishEmployeeUpdated(anyLong());
  }

  @Test
  void refusesToDeleteAnEmployeeWithAManagerToken() throws Exception {
    Employee saved = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            delete("/api/employees/" + saved.getId()).with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    assertThat(repository.existsById(saved.getId())).isTrue();
    verify(eventPublisher, never()).publishEmployeeDeleted(anyLong());
  }

  /** A plain employee has even less authority than a manager, on every mutating endpoint. */
  @Test
  void refusesEveryEmployeeMutationToAnEmployeeToken() throws Exception {
    RequestPostProcessor asEmployee = BearerTokens.forRole(tokens, "EMPLOYEE");

    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"salary\":1.0}")
                .with(asEmployee))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/employees/4242")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"salary\":1.0}")
                .with(asEmployee))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/employees/4242").with(asEmployee))
        .andExpect(status().isForbidden());
  }

  /** HR is the non-ADMIN half of {@code hasAnyRole('ADMIN','HR')}, so it must be let through. */
  @Test
  void allowsAnHrTokenToCreateAnEmployee() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Asha Rao\",\"departmentId\":\"3\",\"salary\":75000.0}")
                .with(BearerTokens.forRole(tokens, "HR")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Asha Rao"));
  }

  /**
   * The directory itself stays open to any authenticated role, but pay is withheld: a MANAGER
   * browsing the org chart sees who works where, with no {@code salary} key at all. Asserted as
   * absent rather than null/zero, so "we forgot to strip it" cannot pass.
   */
  @Test
  void hidesSalaryFromAManagerListingTheDirectory() throws Exception {
    repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(get("/api/employees").with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("Asha Rao"))
        .andExpect(jsonPath("$[0].departmentId").value("3"))
        .andExpect(jsonPath("$[0].salary").doesNotExist());
  }

  /** Same for a plain EMPLOYEE, and on the single-record endpoint for someone else's record. */
  @Test
  void hidesSalaryFromAnEmployeeReadingAnotherRecord() throws Exception {
    Employee other = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(get("/api/employees").with(BearerTokens.forRole(tokens, "EMPLOYEE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].salary").doesNotExist());

    mockMvc
        .perform(
            get("/api/employees/" + other.getId()).with(BearerTokens.forRole(tokens, "EMPLOYEE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(other.getId().intValue()))
        .andExpect(jsonPath("$.name").value("Asha Rao"))
        .andExpect(jsonPath("$.salary").doesNotExist());
  }

  /** The self-view exception: your own salary is yours, matched on the employeeId claim (§4.2). */
  @Test
  void showsSalaryWhenAnEmployeeViewsTheirOwnRecord() throws Exception {
    Employee self = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            get("/api/employees/" + self.getId())
                .with(BearerTokens.forRoleAndEmployee(tokens, "EMPLOYEE", self.getId())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").value(75000.0));
  }

  /** A MANAGER gets no self-view bonus - they are simply not entitled to others' pay either. */
  @Test
  void hidesSalaryFromAManagerReadingAnotherRecord() throws Exception {
    Employee other = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            get("/api/employees/" + other.getId()).with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").doesNotExist());
  }

  /** ADMIN and HR keep full visibility of the directory, salary included. */
  @Test
  void showsSalaryInTheDirectoryToAdminAndHr() throws Exception {
    repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(get("/api/employees").with(BearerTokens.forRole(tokens, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].salary").value(75000.0));

    mockMvc
        .perform(get("/api/employees").with(BearerTokens.forRole(tokens, "HR")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].salary").value(75000.0));
  }

  /**
   * A token with no employeeId claim (issued before the caller was linked to a record) must not be
   * treated as a self-view of any id, or the exception would become a way to read anyone's pay.
   */
  @Test
  void hidesSalaryWhenTheTokenHasNoEmployeeIdClaim() throws Exception {
    Employee other = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

    mockMvc
        .perform(
            get("/api/employees/" + other.getId())
                .with(BearerTokens.forRoleAndEmployee(tokens, "EMPLOYEE", null)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.salary").doesNotExist());
  }

  @Test
  void unreadableJsonIsABadRequestNotAServerError() throws Exception {
    mockMvc
        .perform(
            post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.path").value("/api/employees"));
  }
}
