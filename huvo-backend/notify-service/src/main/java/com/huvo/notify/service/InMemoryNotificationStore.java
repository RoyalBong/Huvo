package com.huvo.notify.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.huvo.notify.domain.Notification;

import lombok.extern.slf4j.Slf4j;

/**
 * The in-memory feed.
 *
 * <p>Selected by configuration when no DynamoDB table is set, so the service starts with no AWS
 * credentials at all - which is what lets the build agent run the tests (Section 9) and lets a
 * developer run it before provisioning anything.
 *
 * <p><b>Not for a pilot.</b> A restart loses the feed, and a notification that only ever existed in
 * memory was never really delivered to anyone who was not watching the screen at the time. Set
 * {@code NOTIFICATIONS_TABLE} for anything real; the warning at construction exists so a running
 * instance is never mistaken for a durable one.
 */
@Slf4j
public class InMemoryNotificationStore implements NotificationStore {

  /** Newest first per user, so a read is a copy of the head of the list. */
  private final Map<String, CopyOnWriteArrayList<Notification>> feeds = new ConcurrentHashMap<>();

  public InMemoryNotificationStore() {
    log.warn(
        "No DynamoDB table configured, so the notification feed is in-memory and is LOST ON "
            + "RESTART. This is for local work and tests only - set NOTIFICATIONS_TABLE "
            + "(e.g. huvo-dev-notifications) for anything a user would be relying on.");
  }

  @Override
  public void save(Notification notification) {
    feeds
        .computeIfAbsent(notification.userId(), key -> new CopyOnWriteArrayList<>())
        .add(0, notification);
  }

  @Override
  public List<Notification> recent(String userId, int limit) {
    List<Notification> feed = feeds.get(userId);
    if (feed == null) {
      return List.of();
    }
    List<Notification> copy = new ArrayList<>(feed);
    copy.sort(Comparator.comparing(Notification::occurredAt).reversed());
    return copy.size() <= limit ? copy : copy.subList(0, limit);
  }
}
