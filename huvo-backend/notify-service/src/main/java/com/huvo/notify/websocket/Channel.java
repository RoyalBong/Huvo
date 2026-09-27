package com.huvo.notify.websocket;

/**
 * The channels multiplexed over the single WebSocket connection (Huvo_Frontend_Context.md Section
 * 5.3).
 *
 * <p>One socket, not one per feature. The frontend opens {@code /ws/notify} once and subscribes to
 * the channels it has screens for, so it is not managing five socket lifecycles, five reconnection
 * strategies and five auth handshakes.
 *
 * <p>The names are wire format. The frontend subscribes with this exact string, so renaming a
 * constant here is a breaking change for every connected client and must be coordinated with the
 * frontend, not done quietly.
 *
 * <p>{@link #CHAT} is declared here even though chat-service does not exist yet: the frontend's
 * {@code realtime/} client is written against the full list, and adding the channel later is
 * additive. A subscription to a channel with no producer simply receives nothing, which is the
 * correct behaviour for a client that asked for it early.
 */
public enum Channel {

  /** Live attendance status changes, late and absent flags. */
  ATTENDANCE("attendance"),

  /** Task status changes and new assignments. */
  TASKS("tasks"),

  /** Leave approval and rejection updates. */
  LEAVE("leave"),

  /** Messages, presence and typing indicators. Produced by chat-service when it exists. */
  CHAT("chat"),

  /** The in-app notification feed itself. */
  NOTIFICATIONS("notifications");

  private final String wireName;

  Channel(String wireName) {
    this.wireName = wireName;
  }

  /** The exact string the frontend sends and receives. */
  public String wireName() {
    return wireName;
  }

  /**
   * The channel for a wire name, or null when it is not one of ours.
   *
   * <p>Returns null rather than throwing: a client asking for a channel this build does not know
   * should be told which ones exist, not have its connection dropped.
   *
   * @param wireName the name the client sent
   * @return the channel, or null
   */
  public static Channel fromWireName(String wireName) {
    for (Channel channel : values()) {
      if (channel.wireName.equals(wireName)) {
        return channel;
      }
    }
    return null;
  }

  /** Every channel's wire name, for a client enumerating what it may subscribe to. */
  public static java.util.List<String> allWireNames() {
    return java.util.Arrays.stream(values()).map(Channel::wireName).toList();
  }
}
