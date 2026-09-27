package com.huvo.notify.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import com.huvo.notify.domain.Notification;

/**
 * The in-memory feed.
 *
 * <p>Bound when no DynamoDB table is configured, so the service starts and its listeners run
 * without AWS credentials - which is what lets the build agent run the tests (Section 9) and lets a
 * developer run it locally before provisioning anything.
 *
 * <p>Not a cache and not a fallback to production DynamoDB: a process restart loses the feed. It is
 * the local and test implementation, chosen by configuration, and the log line says which one is in
 * use so nobody mistakes a running instance for a working one.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "huvo.notifications.table",
    havingValue = "",
    matchIfMissing = true)
public class InMemoryNotificationStore implements NotificationStore {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(InMemoryNotificationStore.class);

  /** Newest first per user, so a read is a copy of the head of the list. */
  private final Map<String, CopyOnWriteArrayList<Notification>> feeds = new ConcurrentHashMap<>();

  public InMemoryNotificationStore() {
    log.warn(
        "No DynamoDB table configured, so the notification feed is in-memory and is lost on "
            + "restart. Set NOTIFICATIONS_TABLE for anything beyond local use.");
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
