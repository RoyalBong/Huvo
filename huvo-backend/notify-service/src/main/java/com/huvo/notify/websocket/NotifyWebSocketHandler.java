package com.huvo.notify.websocket;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huvo.security.HuvoPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The single WebSocket endpoint, {@code /ws/notify} (Huo_Frontend_Context.md Section 5.3).
 *
 * <p>Client frames are JSON with a {@code type} and a {@code channels} list:
 *
 * <pre>
 * {"type":"subscribe","channels":["attendance","tasks"]}
 * {"type":"unsubscribe","channels":["chat"]}
 * </pre>
 *
 * <p>Server frames carry a {@code type}, a {@code channel} and {@code details}, so the frontend's
 * {@code realtime/} client routes off one connection without inspecting message shapes.
 *
 * <p>The principal comes from session attributes the handshake interceptor populated, never from a
 * client frame - a client able to name its own identity could subscribe as anyone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyWebSocketHandler extends TextWebSocketHandler {

  /** Set by the handshake interceptor once the token has been validated. */
  public static final String PRINCIPAL_ATTRIBUTE = "huvo.principal";

  private final WebSocketHub hub;
  private final ObjectMapper objectMapper;

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    HuvoPrincipal principal = principalOf(session);
    if (principal == null) {
      // Unreachable in practice: the interceptor rejects an unauthenticated handshake before this.
      // Closing anyway is correct - a session with no principal is one the hub can never route to
      // anyone, and leaving it open is a socket that receives everything and tells no one.
      log.warn("Rejecting a WebSocket session with no authenticated principal");
      closeQuietly(session);
      return;
    }
    // Nothing is subscribed until the client asks. A client that connects and never subscribes
    // receives nothing, which is right - a connection is not an implicit subscription to everything
    // about the company.
    hub.register(
        new WebSocketHub.Connection(
            session.getId(), principal, Set.of(), message -> sendQuietly(session, message)));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    if (principalOf(session) == null) {
      closeQuietly(session);
      return;
    }

    ClientCommand command;
    try {
      command = objectMapper.readValue(message.getPayload(), ClientCommand.class);
    } catch (Exception e) {
      // Answered, not silently dropped. The frontend's "reconnecting" state is about the socket; a
      // client sending nonsense needs to hear that its frame was rejected.
      sendQuietly(session, error("Client message was not valid JSON"));
      return;
    }

    Set<Channel> channels = resolve(command.channels());
    String type = command.type() == null ? "" : command.type();
    switch (type) {
      case "subscribe" -> {
        hub.subscribe(session.getId(), channels);
        sendQuietly(session, ack("subscribed", channels));
      }
      case "unsubscribe" -> {
        hub.unsubscribe(session.getId(), channels);
        sendQuietly(session, ack("unsubscribed", channels));
      }
      case "ping" -> sendQuietly(session, ack("pong", Set.of()));
      default -> sendQuietly(session, error("Unknown message type: " + command.type()));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    // The leak guard. Without this the hub grows for the life of the process.
    hub.unregister(session.getId());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    // A connection dropped mid-write is normal on mobile networks. It must not leave the session
    // registered, and must not propagate as a server error.
    log.debug("WebSocket transport error on {}: {}", session.getId(), exception.toString());
    hub.unregister(session.getId());
  }

  /**
   * Turns wire names into channels, skipping any this build does not know.
   *
   * <p>Skipped rather than fatal on purpose: the frontend is written against the full channel list
   * from Section 5.3 and may run ahead of this backend, and dropping its connection over a channel
   * that has no producer yet would be a worse outcome than silence on that one channel.
   */
  private Set<Channel> resolve(Set<String> requested) {
    Set<Channel> resolved = new LinkedHashSet<>();
    if (requested == null) {
      return resolved;
    }
    for (String name : requested) {
      Channel channel = Channel.fromWireName(name);
      if (channel != null) {
        resolved.add(channel);
      }
    }
    return resolved;
  }

  private HuvoPrincipal principalOf(WebSocketSession session) {
    Object principal = session.getAttributes().get(PRINCIPAL_ATTRIBUTE);
    return principal instanceof HuvoPrincipal p ? p : null;
  }

  private String ack(String type, Set<Channel> channels) {
    Set<String> names = new LinkedHashSet<>();
    channels.forEach(channel -> names.add(channel.wireName()));
    return json(new ServerMessage(type, null, names));
  }

  private String error(String message) {
    return json(new ServerMessage("error", null, java.util.Set.of(message)));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      log.error("Could not serialise a WebSocket frame", e);
      return "{\"type\":\"error\"}";
    }
  }

  /** Writes to the socket, swallowing a failure on one that has already gone. */
  private void sendQuietly(WebSocketSession session, String message) {
    try {
      if (session.isOpen()) {
        session.sendMessage(new TextMessage(message));
      }
    } catch (Exception e) {
      log.debug("Dropping a write to closed session {}: {}", session.getId(), e.toString());
      hub.unregister(session.getId());
    }
  }

  private void closeQuietly(WebSocketSession session) {
    try {
      session.close(CloseStatus.POLICY_VIOLATION);
    } catch (Exception e) {
      log.debug("Could not close session {}: {}", session.getId(), e.toString());
    }
    hub.unregister(session.getId());
  }

  /**
   * A frame from the client.
   *
   * @param type {@code subscribe}, {@code unsubscribe} or {@code ping}
   * @param channels wire names, kept as strings so an unknown one is a runtime skip rather than a
   *     parse failure that would drop a frame that was otherwise fine
   */
  public record ClientCommand(String type, Set<String> channels) {}

  /**
   * A frame from this service.
   *
   * @param type the frame type
   * @param channel the channel it belongs to, null on control frames
   * @param details the channels affected, or an error message
   */
  public record ServerMessage(String type, String channel, java.util.Set<String> details) {}
}
