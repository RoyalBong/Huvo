package com.huvo.notify.service;

import com.huvo.notify.domain.Notification;

/**
 * The in-app feed store (Section 3.2: DynamoDB).
 *
 * <p>A port rather than a concrete client, for one reason: Section 8.1 forbids emulating DynamoDB
 * locally, so the DynamoDB implementation cannot be exercised in the test suite. Everything above
 * this interface - the listeners, the routing, the WebSocket hub - is testable against an in-memory
 * implementation, and this interface is where the real one is bound.
 */
public interface NotificationStore {

  /**
   * Appends a notification to a user's feed.
   *
   * @param notification the entry to store
   */
  void save(Notification notification);

  /**
   * A user's feed, newest first.
   *
   * @param userId whose feed
   * @param limit the most recent entries to return
   * @return the feed, newest first
   */
  java.util.List<Notification> recent(String userId, int limit);
}
