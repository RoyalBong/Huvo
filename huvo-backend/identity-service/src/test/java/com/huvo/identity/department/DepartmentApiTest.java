package com.huvo.identity.department;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.huvo.identity.department.entity.Department;
import com.huvo.identity.department.messaging.DepartmentEventPublisher;
import com.huvo.identity.department.repository.DepartmentRepository;
import com.huvo.identity.support.BearerTokens;
import com.huvo.security.HuvoTokenService;

/**
 * Contract tests for the department API: DTOs in and out, validation, the single documented error
 * body (Huvo Backend_Context.md §10) and the 404 paths.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DepartmentApiTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private DepartmentRepository repository;

  @Autowired private HuvoTokenService tokens;

  /** The broker is an external boundary and is not needed for these assertions. */
  @MockitoBean private DepartmentEventPublisher eventPublisher;

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
  void createsADepartmentWith201AndReturnsTheRepresentation() throws Exception {
    mockMvc
        .perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering\",\"location\":\"Bengaluru\"}")
                .with(auth))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Engineering"))
        .andExpect(jsonPath("$.location").value("Bengaluru"));

    Department saved = repository.findAll().get(0);
    verify(eventPublisher).publishDepartmentCreated(saved.getId());
  }

  @Test
  void rejectsABlankNameWithTheDocumentedErrorBody() throws Exception {
    mockMvc
        .perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"location\":\"Bengaluru\"}")
                .with(auth))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.message").value("Request validation failed"))
        .andExpect(jsonPath("$.path").value("/api/departments"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.details[0]").value("name: name must not be blank"));
  }

  @Test
  void listsEveryDepartment() throws Exception {
    repository.save(new Department(null, "Engineering", "Bengaluru"));
    repository.save(new Department(null, "Finance", "Mumbai"));

    mockMvc
        .perform(get("/api/departments").with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void returns404ForAnUnknownDepartment() throws Exception {
    mockMvc
        .perform(get("/api/departments/4242").with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("NOT_FOUND"))
        .andExpect(jsonPath("$.message").value(containsString("4242")))
        .andExpect(jsonPath("$.path").value("/api/departments/4242"))
        .andExpect(jsonPath("$.details").doesNotExist());
  }

  @Test
  void updateReplacesTheDepartmentRepresentation() throws Exception {
    Department saved = repository.save(new Department(null, "Engineering", "Bengaluru"));

    mockMvc
        .perform(
            put("/api/departments/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Platform Engineering\",\"location\":\"Pune\"}")
                .with(auth))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(saved.getId().intValue()))
        .andExpect(jsonPath("$.name").value("Platform Engineering"))
        .andExpect(jsonPath("$.location").value("Pune"));

    verify(eventPublisher).publishDepartmentUpdated(saved.getId());
  }

  @Test
  void updateOnAnUnknownDepartmentIs404() throws Exception {
    mockMvc
        .perform(
            put("/api/departments/4242")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Platform Engineering\"}")
                .with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void deleteRemovesTheDepartmentAndIs404OnTheSecondAttempt() throws Exception {
    Department saved = repository.save(new Department(null, "Engineering", "Bengaluru"));

    mockMvc
        .perform(delete("/api/departments/" + saved.getId()).with(auth))
        .andExpect(status().isNoContent());

    assertThat(repository.existsById(saved.getId())).isFalse();
    verify(eventPublisher).publishDepartmentDeleted(saved.getId());

    mockMvc
        .perform(delete("/api/departments/" + saved.getId()).with(auth))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  /**
   * The org structure is HR-maintained (Section 4.1). A MANAGER can see the tree but not reshape
   * it, so the token must be refused before the handler runs.
   */
  @Test
  void refusesToCreateADepartmentWithAManagerToken() throws Exception {
    mockMvc
        .perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering\",\"location\":\"Bengaluru\"}")
                .with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.error").value("FORBIDDEN"))
        .andExpect(jsonPath("$.path").value("/api/departments"));

    assertThat(repository.count()).isZero();
    verify(eventPublisher, never()).publishDepartmentCreated(anyLong());
  }

  @Test
  void refusesToUpdateADepartmentWithAManagerToken() throws Exception {
    Department saved = repository.save(new Department(null, "Engineering", "Bengaluru"));

    mockMvc
        .perform(
            put("/api/departments/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Platform Engineering\"}")
                .with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    // The row is untouched, not merely reported as refused.
    assertThat(repository.findById(saved.getId()).orElseThrow().getName()).isEqualTo("Engineering");
    verify(eventPublisher, never()).publishDepartmentUpdated(anyLong());
  }

  @Test
  void refusesToDeleteADepartmentWithAManagerToken() throws Exception {
    Department saved = repository.save(new Department(null, "Engineering", "Bengaluru"));

    mockMvc
        .perform(
            delete("/api/departments/" + saved.getId())
                .with(BearerTokens.forRole(tokens, "MANAGER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("FORBIDDEN"));

    assertThat(repository.existsById(saved.getId())).isTrue();
    verify(eventPublisher, never()).publishDepartmentDeleted(anyLong());
  }

  /** A plain employee has even less authority than a manager, on every mutating endpoint. */
  @Test
  void refusesEveryDepartmentMutationToAnEmployeeToken() throws Exception {
    RequestPostProcessor asEmployee = BearerTokens.forRole(tokens, "EMPLOYEE");

    mockMvc
        .perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering\"}")
                .with(asEmployee))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/departments/4242")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Platform Engineering\"}")
                .with(asEmployee))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/departments/4242").with(asEmployee))
        .andExpect(status().isForbidden());
  }

  /** HR is the non-ADMIN half of {@code hasAnyRole('ADMIN','HR')}, so it must be let through. */
  @Test
  void allowsAnHrTokenToCreateADepartment() throws Exception {
    mockMvc
        .perform(
            post("/api/departments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Engineering\",\"location\":\"Bengaluru\"}")
                .with(BearerTokens.forRole(tokens, "HR")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Engineering"));
  }

  /**
   * Reads are deliberately still open to any authenticated role while the directory-scope question
   * is pending - the frontend needs the full list for pickers. If the endpoints get scoped later,
   * this test is the reminder to update it deliberately.
   */
  @Test
  void stillLetsAnEmployeeTokenReadTheDepartments() throws Exception {
    repository.save(new Department(null, "Engineering", "Bengaluru"));

    mockMvc
        .perform(get("/api/departments").with(BearerTokens.forRole(tokens, "EMPLOYEE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }
}
