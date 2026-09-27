package com.huvo.notify.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.huvo.notify.domain.Notification;
import com.huvo.notify.websocket.Channel;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * The durable feed, in DynamoDB (Huo_Backend_Context.md Sections 3.2, 3.4, 8.1).
 *
 * <p>One item per notification. The table is keyed {@code userId} (partition) then {@code
 * occurredAt#id} (sort) - the same shape {@code huvo-audit-client} uses, and for the same reason: a
 * user's whole feed is one query with no scan, and the sort key makes the newest entries a {@code
 * Limit} rather than a filter. The {@code #id} suffix matters more here than in the audit table -
 * two notifications for the same user raised in the same millisecond would otherwise collide on the
 * sort key and one would silently overwrite the other.
 *
 * <p><b>Real AWS, no emulation.</b> Section 8.1 rules out DynamoDB Local, so this talks to the
 * actual dev table ({@code huvo-dev-notifications}) through the default provider chain - the
 * developer's {@code AWS_PROFILE} locally, the instance role on EC2. It is therefore exercised in
 * development against real infrastructure and <em>not</em> in the unit suite, which is the
 * trade-off Section 9 asks for: the build agent must not need AWS credentials. The item shape
 * itself is unit tested, because the column names are the contract.
 *
 * <p>Written with explicit {@link AttributeValue} maps rather than a marshaller, mirroring {@code
 * huvo-audit-client}, so the stored column names are visible next to the contract the reading side
 * depends on.
 */
public class DynamoNotificationStore implements NotificationStore {

  /** Partition key: whose feed this item belongs to. */
  public static final String PARTITION_KEY = "userId";

  /** Sort key: {@code occurredAt#id}, newest-sortable and collision-free. */
  public static final String SORT_KEY = "sortKey";

  private final DynamoDbClient dynamo;
  private final String tableName;

  /**
   * @param dynamo a configured client, owned and closed by the calling service
   * @param tableName the table, e.g. {@code huvo-dev-notifications} in dev and {@code
   *     huvo_notifications} in prod
   */
  public DynamoNotificationStore(DynamoDbClient dynamo, String tableName) {
    if (dynamo == null) {
      throw new IllegalArgumentException("A DynamoDbClient is required");
    }
    if (tableName == null || tableName.isBlank()) {
      throw new IllegalArgumentException("A notifications table name is required");
    }
    this.dynamo = dynamo;
    this.tableName = tableName;
  }

  @Override
  public void save(Notification notification) {
    dynamo.putItem(
        PutItemRequest.builder().tableName(tableName).item(toItem(notification)).build());
  }

  /**
   * A user's most recent notifications, newest first.
   *
   * <p>{@code ScanIndexForward = false} gets the newest items straight from the index rather than
   * reading a partition forwards and reversing it - the difference between a bounded read and one
   * that walks an entire feed.
   */
  @Override
  public List<Notification> recent(String userId, int limit) {
    QueryResponse response =
        dynamo.query(
            QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression(PARTITION_KEY + " = :userId")
                .expressionAttributeValues(
                    Map.of(":userId", AttributeValue.builder().s(userId).build()))
                .scanIndexForward(false)
                .limit(limit)
                .consistentRead(false)
                .build());

    List<Notification> feed = new ArrayList<>();
    for (Map<String, AttributeValue> item : response.items()) {
      feed.add(fromItem(item));
    }
    return feed;
  }

  /** The sort key for one notification. */
  static String sortKey(Notification notification) {
    return notification.occurredAt() + "#" + notification.id();
  }

  /**
   * The table row shape.
   *
   * <p>Written out rather than marshalled so the column names are visible here, next to the
   * contract the reading side depends on.
   */
  static Map<String, AttributeValue> toItem(Notification notification) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put(PARTITION_KEY, string(notification.userId()));
    item.put(SORT_KEY, string(sortKey(notification)));
    item.put("id", string(notification.id()));
    item.put("channel", string(notification.channel().wireName()));
    item.put("eventType", string(notification.eventType()));
    item.put("title", string(notification.title()));
    item.put("body", string(notification.body()));
    item.put("occurredAt", string(notification.occurredAt().toString()));
    item.put("read", AttributeValue.builder().bool(notification.read()).build());
    return item;
  }

  /** Reads a row back into a notification. */
  static Notification fromItem(Map<String, AttributeValue> item) {
    return new Notification(
        text(item, "id"),
        text(item, PARTITION_KEY),
        Channel.fromWireName(text(item, "channel")),
        text(item, "eventType"),
        text(item, "title"),
        text(item, "body"),
        java.time.OffsetDateTime.parse(text(item, "occurredAt")),
        bool(item, "read"));
  }

  private static String text(Map<String, AttributeValue> item, String key) {
    AttributeValue value = item.get(key);
    return value == null ? null : value.s();
  }

  private static boolean bool(Map<String, AttributeValue> item, String key) {
    AttributeValue value = item.get(key);
    return value != null && Boolean.TRUE.equals(value.bool());
  }

  /**
   * DynamoDB cannot store a null string, and a null here means the row is unreadable rather than
   * blank. An explicit marker keeps "the producer had no title" distinguishable from "the title is
   * missing because the write was wrong" - the same reasoning as huvo-audit-client.
   */
  private static AttributeValue string(String value) {
    return AttributeValue.builder().s(value == null ? "<none>" : value).build();
  }
}
