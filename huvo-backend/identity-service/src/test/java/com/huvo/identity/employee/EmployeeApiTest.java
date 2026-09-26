package com.huvo.identity.employee;

import com.huvo.identity.employee.entity.Employee;
import com.huvo.identity.employee.messaging.EmployeeEventPublisher;
import com.huvo.identity.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the employee API: DTOs in and out, validation, the single
 * documented error body (Huvo Backend_Context.md §10) and the 404 paths.
 */
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmployeeRepository repository;

    /** The broker is an external boundary and is not needed for these assertions. */
    @MockitoBean
    private EmployeeEventPublisher eventPublisher;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    void createsAnEmployeeWith201AndReturnsTheRepresentation() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha Rao\",\"departmentId\":\"3\",\"salary\":75000.0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.departmentId").value("3"))
                .andExpect(jsonPath("$.salary").value(75000.0));
    }

    @Test
    void rejectsABlankNameWithTheDocumentedErrorBody() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"salary\":10.0}"))
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
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha Rao\",\"salary\":-1.0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value("salary: salary must not be negative"));
    }

    @Test
    void returns404ForAnUnknownEmployee() throws Exception {
        mockMvc.perform(get("/api/employees/4242"))
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

        mockMvc.perform(put("/api/employees/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha Rao Jr\",\"departmentId\":\"4\",\"salary\":80000.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Asha Rao Jr"))
                .andExpect(jsonPath("$.departmentId").value("4"))
                .andExpect(jsonPath("$.salary").value(80000.0));
    }

    @Test
    void updateOnAnUnknownEmployeeIs404() throws Exception {
        mockMvc.perform(put("/api/employees/4242")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Asha Rao\",\"salary\":1.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void deleteRemovesTheEmployeeAndIs404OnTheSecondAttempt() throws Exception {
        Employee saved = repository.save(new Employee(null, "Asha Rao", "3", 75000.0));

        mockMvc.perform(delete("/api/employees/" + saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(repository.existsById(saved.getId())).isFalse();

        mockMvc.perform(delete("/api/employees/" + saved.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    /**
     * Guards the tricky part of the advice: Spring MVC's own exceptions must keep their
     * correct status code while still being rendered in the documented error body.
     */
    @Test
    void frameworkErrorsAlsoUseTheDocumentedErrorBody() throws Exception {
        mockMvc.perform(patch("/api/employees/1"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.path").value("/api/employees/1"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void unreadableJsonIsABadRequestNotAServerError() throws Exception {
        mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/api/employees"));
    }
}
