package com.huvo.identity.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.huvo.audit.AuditClient;
import com.huvo.audit.AuditEntry;
import com.huvo.audit.AuditWriteException;
import com.huvo.security.HuvoPrincipal;

/**
 * The two failure policies are the whole point of this class, and they are easy to break in a way
 * that only shows up during a DynamoDB outage - so both are pinned here.
 */
class AuditRecorderTest {

  private final AuditClient auditClient = mock(AuditClient.class);

  private final AuditRecorder recorder = new AuditRecorder(auditClient);

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  private static void authenticateAs(String userId, String role) {
    HuvoPrincipal principal = new HuvoPrincipal(userId, role, java.util.List.of(1L), 42L);
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  @Test
  void recordPassesTheAuthenticatedCallerThroughAsTheActor() {
    authenticateAs("7", "HR");

    recorder.record("employee.updated", "employee", "42", Map.of("name", "Asha Rao"));

    var captor = org.mockito.ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClient).record(captor.capture());
    AuditEntry written = captor.getValue();
    assertThat(written.actorUserId()).isEqualTo("7");
    assertThat(written.actorRole()).isEqualTo("HR");
    assertThat(written.action()).isEqualTo("employee.updated");
    assertThat(written.entity()).isEqualTo("employee");
    assertThat(written.entityId()).isEqualTo("42");
    assertThat(written.service()).isEqualTo("identity-service");
  }

  @Test
  void recordSwallowsAWriteFailureBecauseTheMutationAlreadyCommitted() {
    authenticateAs("7", "HR");
    doThrow(new AuditWriteException("table unavailable", null)).when(auditClient).record(any());

    // The best-effort contract: no throw, because the caller cannot un-commit its write.
    assertThatCode(() -> recorder.record("employee.updated", "employee", "42", Map.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void recordRequiredPropagatesAWriteFailure() {
    authenticateAs("7", "ADMIN");
    doThrow(new AuditWriteException("table unavailable", null)).when(auditClient).record(any());

    // The guaranteed contract, for a path where the audit row is the operation.
    assertThatThrownBy(
            () -> recorder.recordRequired("attendance.overridden", "attendance_day", "9", Map.of()))
        .isInstanceOf(AuditWriteException.class);
  }

  @Test
  void recordRequiredSucceedsWhenTheWriteSucceeds() {
    authenticateAs("7", "ADMIN");

    assertThatCode(
            () -> recorder.recordRequired("attendance.overridden", "attendance_day", "9", Map.of()))
        .doesNotThrowAnyException();
    verify(auditClient).record(any());
  }

  @Test
  void aNullActorIsAllowedForASystemInitiatedChange() {
    // No authentication in the context at all - a scheduled job, not a request.
    recorder.record("late_tracker.reset", "late_tracker", "all", Map.of());

    var captor = org.mockito.ArgumentCaptor.forClass(AuditEntry.class);
    verify(auditClient).record(captor.capture());
    assertThat(captor.getValue().actorUserId()).isNull();
    assertThat(captor.getValue().actorRole()).isNull();
  }

  @Test
  void recordFailsOpenWhenAuditIsNotConfiguredAtAll() {
    // A build agent has no AWS credentials; CRUD must still work.
    AuditRecorder unconfigured = new AuditRecorder(null);

    assertThatCode(() -> unconfigured.record("employee.created", "employee", "1", Map.of()))
        .doesNotThrowAnyException();
  }

  @Test
  void recordRequiredFailsLoudlyWhenAuditIsNotConfiguredAtAll() {
    // Silently skipping a required audit would hide a deployment mistake.
    AuditRecorder unconfigured = new AuditRecorder(null);

    assertThatThrownBy(
            () ->
                unconfigured.recordRequired(
                    "attendance.overridden", "attendance_day", "9", Map.of()))
        .isInstanceOf(AuditWriteException.class)
        .hasMessageContaining("huvo.audit.table");
  }

  @Test
  void anUnconfiguredRecorderNeverTouchesTheClient() {
    AuditRecorder unconfigured = new AuditRecorder(mock(AuditClient.class));

    assertThatCode(() -> unconfigured.record("employee.created", "employee", "1", Map.of()))
        .doesNotThrowAnyException();
    verifyNoInteractions(auditClient);
  }
}
