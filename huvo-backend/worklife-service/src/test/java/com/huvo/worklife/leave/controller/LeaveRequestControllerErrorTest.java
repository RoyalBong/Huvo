package com.huvo.worklife.leave.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.exception.GlobalExceptionHandler;
import com.huvo.worklife.exception.LeaveConflictException;
import com.huvo.worklife.exception.NotFoundException;
import com.huvo.worklife.leave.service.LeaveService;

/**
 * The HTTP rendering of leave failures, and specifically the 409 for an overlapping request.
 *
 * <p>Standalone MockMvc rather than a full {@code @SpringBootTest}: the thing under test is the
 * advice's mapping of an exception onto a status and a body, and booting the whole context to check
 * that would prove less, not more, while costing the database.
 *
 * <p>This exists because the service-level test can only assert that an exception is thrown. If the
 * advice mapped it to 400, or rendered a message with no dates in it, every service-level test
 * would still pass and the employee would still get a useless error.
 */
class LeaveRequestControllerErrorTest {

  private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);
  private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);

  private final LeaveService leave = org.mockito.Mockito.mock(LeaveService.class);
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new LeaveRequestController(leave))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  private static UsernamePasswordAuthenticationToken auth() {
    HuvoPrincipal principal = new HuvoPrincipal("u-42", "EMPLOYEE", List.of(3L), 42L);
    return new UsernamePasswordAuthenticationToken(
        principal, null, List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));
  }

  private static String body(LocalDate from, LocalDate to) {
    return """
        {"leaveType":"ANNUAL","from":"%s","to":"%s","reason":"Family trip"}
        """
        .formatted(from, to);
  }

  @Test
  void anOverlappingRequestIsAConflictNotABadRequest() throws Exception {
    when(leave.apply(any(), eq("ANNUAL"), eq(MONDAY), eq(WEDNESDAY), any()))
        .thenThrow(
            new LeaveConflictException(
                7L, com.huvo.worklife.leave.LeaveStatus.PENDING, MONDAY, WEDNESDAY));

    mvc.perform(
            post("/api/leave")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MONDAY, WEDNESDAY)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.error").value("LEAVE_OVERLAP"));
  }

  @Test
  void theConflictBodyNamesTheConflictingDates() throws Exception {
    when(leave.apply(any(), eq("ANNUAL"), eq(MONDAY), eq(WEDNESDAY), any()))
        .thenThrow(
            new LeaveConflictException(
                7L, com.huvo.worklife.leave.LeaveStatus.PENDING, MONDAY, WEDNESDAY));

    mvc.perform(
            post("/api/leave")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(MONDAY, WEDNESDAY)))
        // Machine-readable, so the client can show the taken days without parsing prose.
        .andExpect(jsonPath("$.details[0]").value("conflictingRequestId: 7"))
        .andExpect(jsonPath("$.details[1]").value("conflictingStatus: PENDING"))
        .andExpect(jsonPath("$.details[2]").value("conflictingFrom: 2026-09-28"))
        .andExpect(jsonPath("$.details[3]").value("conflictingTo: 2026-09-30"))
        // And human-readable, so the message is useful on its own.
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2026-09-28")))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("2026-09-30")))
        .andExpect(jsonPath("$.path").value("/api/leave"));
  }

  @Test
  void aPlainBusinessRuleViolationIsABadRequest() throws Exception {
    // So the two are actually distinguishable: if this also returned 409, the conflict status would
    // be carrying no information.
    when(leave.apply(any(), any(), any(), any(), any()))
        .thenThrow(
            new BusinessRuleException("The last day of leave cannot be before the first day"));

    mvc.perform(
            post("/api/leave")
                .principal(auth())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(WEDNESDAY, MONDAY)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  void aMissingRequestIsANotFound() throws Exception {
    when(leave.approve(any(), eq(9999L), any()))
        .thenThrow(new NotFoundException("No leave request 9999"));

    mvc.perform(post("/api/leave/9999/approve").principal(auth()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }
}
