package com.huvo.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class AuditClientTest {

  /**
   * The client is mocked rather than pointed at DynamoDB Local: Section 8.2 explicitly rules that
   * out because it adds behavioural drift, and the real dev table is not reachable from a unit
   * test. What matters here is the request this client builds.
   */
  private final DynamoDbClient dynamo = mock(DynamoDbClient.class);

  private final AuditClient audit = new AuditClient(dynamo, "huvo-dev-audit-log");

  private static AuditEntry entry() {
    return AuditEntry.of("1", "HR", "employee.updated", "employee", "7", "identity-service", null);
  }

  private Map<String, AttributeValue> capturedItem() {
    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(dynamo).putItem(captor.capture());
    return captor.getValue().item();
  }

  @Test
  void writesTheEntryToTheConfiguredTable() {
    when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    audit.record(entry());

    ArgumentCaptor<PutItemRequest> captor = ArgumentCaptor.forClass(PutItemRequest.class);
    verify(dynamo).putItem(captor.capture());
    assertThat(captor.getValue().tableName()).isEqualTo("huvo-dev-audit-log");
  }

  @Test
  void writesTheEntityAsThePartitionKeySoHistoryIsOneQuery() {
    when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    audit.record(entry());

    Map<String, AttributeValue> item = capturedItem();
    assertThat(item.get(AuditEntry.PARTITION_KEY).s()).isEqualTo("employee");
    assertThat(item.get("entityId").s()).isEqualTo("7");
    assertThat(item.get("action").s()).isEqualTo("employee.updated");
    assertThat(item.get("actorUserId").s()).isEqualTo("1");
    assertThat(item.get("actorRole").s()).isEqualTo("HR");
    assertThat(item.get("service").s()).isEqualTo("identity-service");
    assertThat(item.get("eventId").s()).isNotBlank();
    assertThat(item.get("occurredAt").s()).isNotBlank();
    assertThat(item.get(AuditEntry.SORT_KEY).s()).isNotBlank();
  }

  @Test
  void prefixesDetailKeysSoTheyCannotCollideWithTheFixedColumns() {
    when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    audit.record(
        AuditEntry.of(
            "1",
            "HR",
            "employee.updated",
            "employee",
            "7",
            "identity-service",
            Map.of("departmentId", "3")));

    Map<String, AttributeValue> item = capturedItem();
    assertThat(item.get("detail.departmentId").s()).isEqualTo("3");
    // A detail must never be able to overwrite a column the reader depends on.
    assertThat(item).doesNotContainKey("departmentId");
    assertThat(item.get("entityId").s()).isEqualTo("7");
  }

  @Test
  void recordsAMissingFieldExplicitlyRatherThanOmittingIt() {
    when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    audit.record(
        AuditEntry.of(null, null, "employee.created", "employee", "7", "identity-service", null));

    // "system-initiated" and "we forgot" must stay distinguishable in the table.
    Map<String, AttributeValue> item = capturedItem();
    assertThat(item.get("actorUserId").s()).isEqualTo("<none>");
    assertThat(item.get("actorRole").s()).isEqualTo("<none>");
  }

  @Test
  void recordThrowsSoAnOperationThatMustBeAuditedCanFail() {
    doThrow(DynamoDbException.builder().message("table unavailable").build())
        .when(dynamo)
        .putItem(any(PutItemRequest.class));

    assertThatThrownBy(() -> audit.record(entry()))
        .isInstanceOf(AuditWriteException.class)
        .hasMessageContaining("huvo-dev-audit-log");
  }

  @Test
  void recordQuietlyReportsFailureInsteadOfThrowing() {
    doThrow(DynamoDbException.builder().message("table unavailable").build())
        .when(dynamo)
        .putItem(any(PutItemRequest.class));

    assertThat(audit.recordQuietly(entry())).isFalse();
  }

  @Test
  void recordQuietlyReportsSuccess() {
    when(dynamo.putItem(any(PutItemRequest.class))).thenReturn(PutItemResponse.builder().build());

    assertThat(audit.recordQuietly(entry())).isTrue();
  }

  @Test
  void rejectsABlankTableName() {
    assertThatThrownBy(() -> new AuditClient(mock(DynamoDbClient.class), "  "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
