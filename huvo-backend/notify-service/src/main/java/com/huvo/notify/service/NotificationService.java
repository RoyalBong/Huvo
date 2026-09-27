package com.huvo.notify.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.notify.domain.Notification;
import com.huvo.notify.websocket.Channel;
import com.huvo.notify.websocket.WebSocketHub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Builds a notification, records it, and pushes it to whoever should see it.
 *
 * <p>One path for every event, so the two things that are easy to get wrong per-event - scoping the
 * recipient and choosing the channel - are decided once and written down, rather than re-guessed in
 * each of the eight listeners.
 *
 * <p>Push failure never fails the event. The feed is written first and the socket push is
 * best-effort: a user with the tab closed still has the notification when they come back, and a
 * client that misses a live push recovers on reload. The reverse ordering would let one broken
 * socket cost someone a notification permanently.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final NotificationStore store;
  private final WebSocketHub hub;
  private final ObjectMapper objectMapper;

  /**
   * Notifies one user and pushes to their open sockets.
   *
   * @param userId the recipient
   * @param channel the frontend channel
   * @param eventType the routing key it came from
   * @param title the headline
   * @param body the detail line
   * @return the stored notification
   */
  public Notification notify(
      String userId, Channel channel, String eventType, String title, String body) {
    if (userId == null) {
      // No recipient is not a notification. Returning quietly rather than throwing keeps a payload
      // with an unexpected null id from poison-looping the queue.
      log.warn("No recipient for a {} event; dropping", eventType);
      return null;
    }

    Notification notification =
        new Notification(
            UUID.randomUUID().toString(),
            userId,
            channel,
            eventType,
            title,
            body,
            OffsetDateTime.now(),
            false);

    store.save(notification);
    push(notification);
    return notification;
  }

  /**
   * Pushes one notification to every open socket belonging to its recipient.
   *
   * <p>Scoped to the user, never broadcast. A broadcast on this service would put one employee's
   * lateness or leave decision in front of the whole company.
   */
  private void push(Notification notification) {
    try {
      String frame =
          objectMapper.writeValueAsString(
              new Frame("event", notification.channel().wireName(), notification));
      int delivered = hub.publishToUser(notification.channel(), notification.userId(), frame);
      log.debug(
          "Pushed a {} notification to {} open socket(s) for user {}",
          notification.channel().wireName(),
          delivered,
          notification.userId());
    } catch (Exception e) {
      // Already recorded in the feed, so the user is not losing it - only the live update is.
      log.error(
          "Could not push a {} notification to {}",
          notification.eventType(),
          notification.userId(),
          e);
    }
  }

  /** A user's recent feed. */
  public List<Notification> recent(String userId, int limit) {
    return store.recent(userId, limit);
  }

  /**
   * A server frame on the wire.
   *
   * @param type the frame type
   * @param channel the channel it belongs to
   * @param payload the notification
   */
  public record Frame(String type, String channel, Notification payload) {}
}
