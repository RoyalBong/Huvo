package com.huvo.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditEntryTest {

  @Test
  void stampsATimeAndAFreshEventId() {
    OffsetDateTime before = OffsetDateTime.now();
    AuditEntry entry =
        AuditEntry.of("1", "HR", "employee.updated", "employee", "7", "identity-service", null);
    OffsetDateTime after = OffsetDateTime.now();

    // The sort key depends on both, so neither may be null.
    assertThat(entry.occurredAt()).isBetween(before, after);
    assertThat(entry.eventId()).isNotBlank();
  }

  @Test
  void generatesADistinctIdForTwoEntriesInTheSameInstant() {
    AuditEntry first =
        AuditEntry.of("1", "HR", "employee.updated", "employee", "7", "identity-service", null);
    AuditEntry second =
        AuditEntry.of("1", "HR", "employee.updated", "employee", "7", "identity-service", null);

    // Two writes to the same record must never overwrite each other in DynamoDB.
    assertThat(first.sortKey()).isNotEqualTo(second.sortKey());
  }

  @Test
  void sortKeyStartsWithTheTimestampSoHistorySortsChronologically() {
    AuditEntry entry =
        AuditEntry.of("1", "HR", "employee.created", "employee", "7", "identity-service", null);

    assertThat(entry.sortKey()).startsWith(AuditEntry.SORT_KEY + "=");
    assertThat(entry.sortKey()).contains(entry.occurredAt().toString());
    assertThat(entry.sortKey()).endsWith(entry.eventId());
  }

  @Test
  void nullDetailsBecomeAnEmptyMap() {
    // No caller should have to decide between passing null and an empty map.
    AuditEntry entry =
        AuditEntry.of("1", "HR", "employee.created", "employee", "7", "identity-service", null);

    assertThat(entry.details()).isEmpty();
  }

  @Test
  void detailsAreCopiedSoALaterMutationCannotRewriteHistory() {
    Map<String, String> mutable = new java.util.HashMap<>();
    mutable.put("departmentId", "3");

    AuditEntry entry =
        AuditEntry.of("1", "HR", "employee.created", "employee", "7", "identity-service", mutable);
    mutable.put("departmentId", "999");

    // An audit row that can be changed after the fact is not an audit row.
    assertThat(entry.details()).containsEntry("departmentId", "3");
  }

  @Test
  void acceptsANullActorForASystemInitiatedChange() {
    // A scheduled job or bootstrap has no user; "who" is sometimes the system.
    AuditEntry entry =
        AuditEntry.of(null, null, "employee.created", "employee", "7", "identity-service", null);

    assertThat(entry.actorUserId()).isNull();
    assertThat(entry.actorRole()).isNull();
  }

  @Test
  void aNullClientOrTableIsRejectedAtConstruction() {
    assertThatThrownBy(() -> new AuditClient(null, "huvo-audit-log"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
