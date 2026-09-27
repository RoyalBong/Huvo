package com.huvo.notify.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.huvo.notify.domain.Notification;
import com.huvo.notify.websocket.Channel;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * The stored row shape.
 *
 * <p>Section 8.1 rules out DynamoDB Local, so this class is never exercised against a real table in
 * the build. What it <em>can</em> be tested for is the part that is a contract: the column names
 * and the key shape. Those are what a reporting query, a CloudWatch metric or a future read path
 * would depend on, and a rename here would be silent.
 *
 * <p>The round trip is the load-bearing assertion - toItem then fromItem must return an equal
 * notification, or a notification that was stored cannot be read back.
 */
class DynamoNotificationStoreItemTest {

  private static final OffsetDateTime WHEN =
      OffsetDateTime.of(2026, 9, 28, 10, 15, 30, 0, ZoneOffset.UTC);

  private static Notification sample() {
    return new Notification(
        "n-1",
        "42",
        Channel.LEAVE,
        "leave.approved",
        "Leave approved",
        "28th to 30th",
        WHEN,
        false);
  }

  @Test
  void theKeyIsTheUsersIdAndAChronologicalSortKey() {
    Map<String, AttributeValue> item = DynamoNotificationStore.toItem(sample());

    // The partition key is whose feed this is; the sort key is what makes newest-first a Limit.
    assertThat(item.get(DynamoNotificationStore.PARTITION_KEY).s()).isEqualTo("42");
    assertThat(item.get(DynamoNotificationStore.SORT_KEY).s()).startsWith("2026-09-28T10:15:30");
  }

  @Test
  void theSortKeyIsUniquePerNotificationEvenAtTheSameInstant() {
    // The reason for the "#id" suffix. Without it, two notifications for one user raised in the
    // same millisecond collide on the sort key and one silently overwrites the other.
    Notification first =
        new Notification("n-1", "42", Channel.TASKS, "task.assigned", "a", "b", WHEN, false);
    Notification second =
        new Notification("n-2", "42", Channel.TASKS, "task.assigned", "a", "b", WHEN, false);

    assertThat(DynamoNotificationStore.sortKey(first))
        .isNotEqualTo(DynamoNotificationStore.sortKey(second));
  }

  @Test
  void twoUsersWithTheSameNotificationDoNotCollide() {
    Notification alice =
        new Notification("n-1", "1", Channel.TASKS, "task.assigned", "a", "b", WHEN, false);
    Notification bob =
        new Notification("n-1", "2", Channel.TASKS, "task.assigned", "a", "b", WHEN, false);

    // Same id, same instant, different partition - which is the whole point of the partition key.
    assertThat(DynamoNotificationStore.toItem(alice).get(DynamoNotificationStore.PARTITION_KEY).s())
        .isNotEqualTo(
            DynamoNotificationStore.toItem(bob).get(DynamoNotificationStore.PARTITION_KEY).s());
  }

  @Test
  void theChannelIsStoredAsItsWireName() {
    // The frontend's channel string, not the enum constant - a row read by anything other than this
    // JVM has to carry the same value the client subscribes with.
    assertThat(DynamoNotificationStore.toItem(sample()).get("channel").s()).isEqualTo("leave");
  }

  @Test
  void theEventTypeIsStoredSoAClientCanTellLateFromAbsent() {
    assertThat(DynamoNotificationStore.toItem(sample()).get("eventType").s())
        .isEqualTo("leave.approved");
  }

  @Test
  void anItemRoundTripsBackToTheSameNotification() {
    Notification original = sample();

    assertThat(DynamoNotificationStore.fromItem(DynamoNotificationStore.toItem(original)))
        .isEqualTo(original);
  }

  @Test
  void aReadNotificationRoundTripsAsRead() {
    Notification read =
        new Notification("n-1", "42", Channel.LEAVE, "leave.approved", "t", "b", WHEN, true);

    assertThat(DynamoNotificationStore.fromItem(DynamoNotificationStore.toItem(read)).read())
        .isTrue();
  }

  @Test
  void aNullTextFieldIsStoredAsAnExplicitMarker() {
    // DynamoDB rejects a null string outright, so it has to be something. A marker keeps "the
    // producer
    // had no body" distinguishable from "the write was wrong".
    Notification noBody =
        new Notification("n-1", "42", Channel.TASKS, "task.assigned", "title", null, WHEN, false);

    assertThat(DynamoNotificationStore.toItem(noBody).get("body").s()).isEqualTo("<none>");
  }
}
