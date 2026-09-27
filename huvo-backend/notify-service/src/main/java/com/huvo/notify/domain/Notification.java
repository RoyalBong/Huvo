package com.huvo.notify.domain;

import java.time.OffsetDateTime;

import com.huvo.notify.websocket.Channel;

/**
 * One entry in the in-app notification feed (Section 3.2).
 *
 * <p>Ids and a human-readable line only. No domain objects, no copies of the task or attendance
 * row: the feed is a pointer to something that happened, and the client re-fetches the real thing.
 * A notification that carried a full snapshot would go stale the moment the underlying record
 * changed.
 *
 * @param id the notification id, also the DynamoDB sort key component
 * @param userId who it is for. This is the partition key, so one person's feed is one query
 * @param channel which frontend channel it belongs to
 * @param eventType the routing key it came from, so a client can distinguish late from absent
 * @param title the headline
 * @param body the detail line
 * @param occurredAt when the thing happened
 * @param read whether the recipient has read it
 */
public record Notification(
    String id,
    String userId,
    Channel channel,
    String eventType,
    String title,
    String body,
    OffsetDateTime occurredAt,
    boolean read) {}
