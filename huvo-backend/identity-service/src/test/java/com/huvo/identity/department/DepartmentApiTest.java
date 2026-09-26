package com.huvo.identity.department;

import com.huvo.identity.department.entity.Department;
import com.huvo.identity.department.repository.DepartmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the department API: DTOs in and out, validation, the single
 * documented error body (Huvo Backend_Context.md §10) and the 404 paths.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class DepartmentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsADepartmentWith201AndReturnsTheRepresentation() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Engineering\",\"location\":\"Bengaluru\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Engineering"))
                .andExpect(jsonPath("$.location").value("Bengaluru"));
    }

    @Test
    void rejectsABlankNameWithTheDocumentedErrorBody() throws Exception {
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"location\":\"Bengaluru\"}"))
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

        mockMvc.perform(get("/api/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void returns404ForAnUnknownDepartment() throws Exception {
        mockMvc.perform(get("/api/departments/4242"))
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

        mockMvc.perform(put("/api/departments/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform Engineering\",\"location\":\"Pune\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Platform Engineering"))
                .andExpect(jsonPath("$.location").value("Pune"));
    }

    @Test
    void updateOnAnUnknownDepartmentIs404() throws Exception {
        mockMvc.perform(put("/api/departments/4242")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Platform Engineering\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void deleteRemovesTheDepartmentAndIs404OnTheSecondAttempt() throws Exception {
        Department saved = repository.save(new Department(null, "Engineering", "Bengaluru"));

        mockMvc.perform(delete("/api/departments/" + saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(repository.existsById(saved.getId())).isFalse();

        mockMvc.perform(delete("/api/departments/" + saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
