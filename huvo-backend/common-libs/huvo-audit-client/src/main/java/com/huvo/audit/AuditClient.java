package com.huvo.audit;

import java.util.HashMap;
import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * Appends entries to the shared {@code huvo_audit_log} DynamoDB table (Huvo_Backend_Context.md
 * Sections 3.4, 7).
 *
 * <p>Framework-free on purpose: the consuming service constructs this with its own {@code
 * DynamoDbClient}, so this module has no Spring dependency and no opinion about how a service is
 * configured. Callers hand in a configured client and a table name.
 *
 * <p><b>Two write methods, because "must the audit write fail the operation?" is a per-call
 * decision, not a library-wide one.</b> {@link #record} throws {@link AuditWriteException} on a
 * transport failure, for paths where the entry is part of the operation's contract (Section 7: an
 * admin override must write an audit entry, so a manual attendance correction that cannot be
 * audited should not be reported as done). {@link #recordQuietly} reports a boolean instead, for
 * ordinary CRUD where DynamoDB being briefly unreachable should not turn an observability
 * dependency into an availability dependency. The second choice is a real trade-off - a mutation
 * can succeed without a trail entry - so the failure is a visible {@code false} the caller can log
 * rather than something buried in the library.
 */
public class AuditClient {

  private final DynamoDbClient dynamo;

  private final String tableName;

  /**
   * @param dynamo a configured client, owned and closed by the calling service
   * @param tableName the table to write to, e.g. {@code huvo-audit-log} in dev and {@code
   *     huvo_audit_log} in prod
   */
  public AuditClient(DynamoDbClient dynamo, String tableName) {
    if (dynamo == null) {
      throw new IllegalArgumentException("A DynamoDbClient is required");
    }
    if (tableName == null || tableName.isBlank()) {
      throw new IllegalArgumentException("An audit table name is required");
    }
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  /**
   * Writes one entry, propagating a transport failure as {@link AuditWriteException}.
   *
   * <p>Use this wherever the audit entry is part of the operation's contract - Section 7 requires
   * an admin override to write an audit entry, so a manual attendance correction that cannot be
   * audited has not happened as far as the trail is concerned, and the caller should fail.
   *
   * @param entry the row to append
   * @throws AuditWriteException when the write did not succeed
   */
  public void record(AuditEntry entry) {
    try {
      dynamo.putItem(PutItemRequest.builder().tableName(tableName).item(toItem(entry)).build());
    } catch (RuntimeException e) {
      throw new AuditWriteException("Could not write the audit entry to " + tableName, e);
    }
  }

  /**
   * Writes one entry, reporting failure instead of throwing.
   *
   * <p>Use this where losing the audit row is better than failing the user's operation. DynamoDB is
   * eventually consistent and can be briefly unavailable, and audit sits on the critical path of
   * every create/update/delete; an observability dependency should not become an availability
   * dependency. The trade-off is deliberate and visible: a mutation can succeed without a trail
   * entry, so the {@code false} return is the caller's signal to log or escalate.
   *
   * @param entry the row to append
   * @return true when the row was accepted, false when the write failed
   */
  public boolean recordQuietly(AuditEntry entry) {
    try {
      record(entry);
      return true;
    } catch (AuditWriteException e) {
      return false;
    }
  }

  /**
   * The table row shape. Written explicitly rather than via a marshaller so the column names are
   * visible here, next to the contract the reading side (reporting, CloudWatch) depends on.
   */
  static Map<String, AttributeValue> toItem(AuditEntry entry) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put(AuditEntry.PARTITION_KEY, string(entry.entity()));
    item.put(AuditEntry.SORT_KEY, string(entry.sortKey()));
    item.put("actorUserId", string(entry.actorUserId()));
    item.put("actorRole", string(entry.actorRole()));
    item.put("action", string(entry.action()));
    item.put("entityId", string(entry.entityId()));
    item.put("occurredAt", string(entry.occurredAt().toString()));
    item.put("eventId", string(entry.eventId()));
    item.put("service", string(entry.service()));
    for (Map.Entry<String, String> detail : entry.details().entrySet()) {
      item.put("detail." + detail.getKey(), string(detail.getValue()));
    }
    return item;
  }

  /**
   * DynamoDB cannot store a null string. Audit is append-only, so a missing field is recorded as an
   * explicit marker rather than dropped - otherwise "system-initiated" and "we forgot" would be
   * indistinguishable in the table.
   */
  private static AttributeValue string(String value) {
    return AttributeValue.builder().s(value == null ? "<none>" : value).build();
  }
}
