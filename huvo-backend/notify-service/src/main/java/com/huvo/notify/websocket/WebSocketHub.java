package com.huvo.notify.websocket;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.huvo.security.HuvoPrincipal;

import lombok.extern.slf4j.Slf4j;

/**
 * Tracks who is connected and what they have subscribed to.
 *
 * <p>One instance holds every open socket, so this is the piece that must not leak: a {@code
 * ConcurrentHashMap} keyed by session id, with the session removed on close. A hub that accumulates
 * closed sessions is an out-of-memory leak that only shows up under load, long after the code that
 * caused it was forgotten.
 *
 * <p>Subscriptions are stored per session rather than assumed, because the frontend subscribes
 * selectively: a user with no chat screen open should not receive chat traffic, and delivering it
 * anyway would be both wasteful and a small leak of conversations.
 */
@Slf4j
public class WebSocketHub {

  /** The sessions currently connected, by session id. */
  private final ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();

  /**
   * A live socket and who owns it.
   *
   * @param sessionId the socket session id
   * @param principal the authenticated caller, from the handshake
   * @param subscriptions the channels this client asked for
   * @param sender how to write to this socket
   */
  public record Connection(
      String sessionId, HuvoPrincipal principal, Set<Channel> subscriptions, Outbound sender) {

    public Connection {
      // Defensive copy: a mutable set handed to a long-lived record would be mutated by whichever
      // thread handles the next subscribe.
      subscriptions = Set.copyOf(subscriptions);
    }
  }

  /** The write side of a socket, so the hub can be tested without a real WebSocket. */
  public interface Outbound {
    void send(String message);
  }

  /** Registers a freshly authenticated socket. */
  public void register(Connection connection) {
    connections.put(connection.sessionId(), connection);
    log.info(
        "WebSocket connected: session={} user={} role={}",
        connection.sessionId(),
        connection.principal().userId(),
        connection.principal().role());
  }

  /** Removes a socket. Safe to call for a session that is already gone. */
  public void unregister(String sessionId) {
    if (connections.remove(sessionId) != null) {
      log.info("WebSocket disconnected: session={}", sessionId);
    }
  }

  /** The number of live sockets. Exposed for the health endpoint and for tests. */
  public int connectionCount() {
    return connections.size();
  }

  /** The live connection for a session, or null. */
  public Connection connection(String sessionId) {
    return connections.get(sessionId);
  }

  /**
   * Widens a session's subscriptions.
   *
   * @param sessionId the session to update
   * @param channels the channels to add
   * @return true when the session was known
   */
  public boolean subscribe(String sessionId, Set<Channel> channels) {
    return update(sessionId, channels, true);
  }

  /**
   * Narrows a session's subscriptions.
   *
   * @param sessionId the session to update
   * @param channels the channels to drop
   * @return true when the session was known
   */
  public boolean unsubscribe(String sessionId, Set<Channel> channels) {
    return update(sessionId, channels, false);
  }

  /**
   * Adds to or removes from a session's subscriptions, replacing the entry in one step.
   *
   * <p>Compute-then-put rather than mutate the set in place, so a concurrent subscribe on another
   * thread cannot interleave and lose one of the two changes.
   */
  private boolean update(String sessionId, Set<Channel> channels, boolean add) {
    Connection current = connections.get(sessionId);
    if (current == null) {
      return false;
    }
    Set<Channel> next = ConcurrentHashMap.newKeySet();
    next.addAll(current.subscriptions());
    if (add) {
      next.addAll(channels);
    } else {
      next.removeAll(channels);
    }
    connections.put(
        sessionId,
        new Connection(current.sessionId(), current.principal(), next, current.sender()));
    return true;
  }

  /**
   * Delivers a message to every connection subscribed to a channel.
   *
   * <p>Delivery is best-effort per connection: one dead socket must not stop the others from being
   * notified, so a write failure drops that session and the loop continues. Anything else turns one
   * broken connection into a silently truncated notification feed for everyone else.
   *
   * @param channel the channel the message belongs to
   * @param message the already-serialized message
   * @return how many connections received it
   */
  public int publish(Channel channel, String message) {
    int delivered = 0;
    for (Connection connection : connections.values()) {
      if (!connection.subscriptions().contains(channel)) {
        continue;
      }
      try {
        connection.sender().send(message);
        delivered++;
      } catch (RuntimeException e) {
        // Almost always a socket closed without its close frame arriving, which is normal on mobile
        // networks and network drops. Drop it and carry on with the rest.
        log.warn("Dropping failed WebSocket session {}: {}", connection.sessionId(), e.toString());
        unregister(connection.sessionId());
      }
    }
    return delivered;
  }

  /**
   * Delivers a message to one user's connections only.
   *
   * <p>The scoping half of the hub. A notification about someone's late arrival must reach them and
   * whoever manages them, and nobody else - a plain broadcast on this service would put one
   * employee's attendance in front of the whole company.
   *
   * @param channel the channel the message belongs to
   * @param userId the recipient's user id
   * @param message the already-serialized message
   * @return how many of that user's connections received it
   */
  public int publishToUser(Channel channel, String userId, String message) {
    int delivered = 0;
    for (Connection connection : connections.values()) {
      if (!connection.subscriptions().contains(channel)) {
        continue;
      }
      if (!userId.equals(connection.principal().userId())) {
        continue;
      }
      try {
        connection.sender().send(message);
        delivered++;
      } catch (RuntimeException e) {
        log.warn("Dropping failed WebSocket session {}: {}", connection.sessionId(), e.toString());
        unregister(connection.sessionId());
      }
    }
    return delivered;
  }
}
