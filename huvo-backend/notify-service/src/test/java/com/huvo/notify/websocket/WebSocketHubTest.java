package com.huvo.notify.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.huvo.security.HuvoPrincipal;

/**
 * The hub's routing, subscription and leak behaviour.
 *
 * <p>These are the rules the frontend's {@code realtime/} client depends on, and each one has a
 * security dimension: the hub holds every connected session and decides who sees what, so "who
 * receives this" is a test, not an implementation detail.
 */
class WebSocketHubTest {

  private WebSocketHub hub;

  /** What a fake socket received. */
  private final List<String> aliceBox = new ArrayList<>();

  private final List<String> bobBox = new ArrayList<>();

  @BeforeEach
  void setUp() {
    hub = new WebSocketHub();
  }

  private void connect(String sessionId, String userId, Set<Channel> channels, List<String> box) {
    hub.register(
        new WebSocketHub.Connection(
            sessionId,
            new HuvoPrincipal(userId, "EMPLOYEE", List.of(3L), Long.valueOf(userId)),
            channels,
            box::add));
  }

  @Test
  void aConnectedClientIsRegistered() {
    connect("s1", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    assertThat(hub.connectionCount()).isEqualTo(1);
    assertThat(hub.connection("s1")).isNotNull();
  }

  @Test
  void aNewConnectionIsSubscribedToNothing() {
    // A connection is not an implicit subscription. If it were, every user would receive every
    // event about every other user the moment they opened the tab.
    connect("s1", "1", Set.of(), aliceBox);

    assertThat(hub.publish(Channel.ATTENDANCE, "{}")).isZero();
  }

  @Test
  void aMessageReachesOnlySubscribersOfThatChannel() {
    connect("s1", "1", Set.of(Channel.ATTENDANCE, Channel.TASKS), aliceBox);

    assertThat(hub.publish(Channel.LEAVE, "{\"x\":1}")).isZero();
    assertThat(aliceBox).isEmpty();

    assertThat(hub.publish(Channel.ATTENDANCE, "{\"x\":1}")).isEqualTo(1);
    assertThat(aliceBox).containsExactly("{\"x\":1}");
  }

  @Test
  void subscribingWidensWhatAClientReceives() {
    connect("s1", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    hub.subscribe("s1", Set.of(Channel.TASKS));

    assertThat(hub.publish(Channel.TASKS, "task")).isEqualTo(1);
    assertThat(aliceBox).containsExactly("task");
  }

  @Test
  void unsubscribingNarrowsWhatAClientReceives() {
    connect("s1", "1", Set.of(Channel.ATTENDANCE, Channel.TASKS), aliceBox);

    hub.unsubscribe("s1", Set.of(Channel.TASKS));

    assertThat(hub.publish(Channel.TASKS, "task")).isZero();
    assertThat(aliceBox).isEmpty();
  }

  @Test
  void subscribingToAnUnknownSessionIsIgnored() {
    assertThat(hub.subscribe("never-connected", Set.of(Channel.ATTENDANCE))).isFalse();
  }

  @Test
  void aUserScopedMessageReachesOnlyThatUser() {
    // The security-relevant one. A broadcast would put one employee's lateness or leave decision in
    // front of the entire company.
    connect("alice", "1", Set.of(Channel.ATTENDANCE), aliceBox);
    connect("bob", "2", Set.of(Channel.ATTENDANCE), bobBox);

    assertThat(hub.publishToUser(Channel.ATTENDANCE, "1", "alice-late")).isEqualTo(1);
    assertThat(aliceBox).containsExactly("alice-late");
    assertThat(bobBox).isEmpty();
  }

  @Test
  void aUserScopedMessageRespectsTheChannelSubscriptionToo() {
    // Being the right user is not enough; they must also have asked for that channel.
    connect("alice", "1", Set.of(Channel.TASKS), aliceBox);

    assertThat(hub.publishToUser(Channel.ATTENDANCE, "1", "late")).isZero();
    assertThat(aliceBox).isEmpty();
  }

  @Test
  void everySessionForOneUserReceivesAMessage() {
    // The same person with the app open on a phone and a laptop should see it on both.
    connect("phone", "1", Set.of(Channel.ATTENDANCE), aliceBox);
    connect("laptop", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    assertThat(hub.publishToUser(Channel.ATTENDANCE, "1", "late")).isEqualTo(2);
  }

  @Test
  void disconnectingRemovesTheSession() {
    connect("s1", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    hub.unregister("s1");

    assertThat(hub.connectionCount()).isZero();
    assertThat(hub.publish(Channel.ATTENDANCE, "late")).isZero();
  }

  @Test
  void unregisteringTwiceIsHarmless() {
    // The close handler and the transport-error handler can both fire for one dead socket. A second
    // remove must not throw, or an ordinary disconnect would surface as a server error.
    connect("s1", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    hub.unregister("s1");
    hub.unregister("s1");

    assertThat(hub.connectionCount()).isZero();
  }

  @Test
  void oneDeadSocketDoesNotStopTheOthersBeingNotified() {
    // A mobile network dropping a connection mid-write is routine. If it stalled the delivery loop,
    // everyone else would silently stop getting notifications too.
    connectDead("dead", "1", Channel.ATTENDANCE);
    connect("alive", "1", Set.of(Channel.ATTENDANCE), aliceBox);

    assertThat(hub.publish(Channel.ATTENDANCE, "late")).isEqualTo(1);
    assertThat(aliceBox).containsExactly("late");
  }

  @Test
  void aDeadSocketIsDroppedAfterAFailedWrite() {
    connectDead("dead", "1", Channel.ATTENDANCE);

    hub.publish(Channel.ATTENDANCE, "late");

    // The leak guard, exercised through the delivery path rather than only via a direct unregister.
    assertThat(hub.connectionCount()).isZero();
  }

  @Test
  void aConnectionCannotBeMutatedThroughItsOwnSubscriptionSet() {
    // The set is defensively copied in the record. If it were not, a caller mutating the set it
    // passed in could silently re-scope a live connection it no longer holds a reference to.
    Set<Channel> mutable = new java.util.HashSet<>(Set.of(Channel.ATTENDANCE));
    WebSocketHub.Connection connection =
        new WebSocketHub.Connection(
            "s1", new HuvoPrincipal("1", "EMPLOYEE", List.of(3L), 1L), mutable, aliceBox::add);

    mutable.add(Channel.CHAT);

    assertThat(connection.subscriptions()).containsExactly(Channel.ATTENDANCE);
  }

  @Test
  void channelWireNamesAreTheOnesTheFrontendSends() {
    // Wire format, pinned. Huo_Frontend_Context.md Section 5.3 names these exact strings, and the
    // frontend subscribes with them, so a rename here is a breaking change for every client.
    assertThat(Channel.allWireNames())
        .containsExactly("attendance", "tasks", "leave", "chat", "notifications");
  }

  @Test
  void anUnknownChannelNameResolvesToNullRatherThanThrowing() {
    // The frontend may be written against a channel list ahead of this backend. Returning null lets
    // the handler skip it; throwing would drop an otherwise good connection.
    assertThat(Channel.fromWireName("payroll")).isNull();
    assertThat(Channel.fromWireName("attendance")).isEqualTo(Channel.ATTENDANCE);
  }

  /** A session whose every write fails, as a socket closed without a close frame does. */
  private void connectDead(String sessionId, String userId, Channel channel) {
    hub.register(
        new WebSocketHub.Connection(
            sessionId,
            new HuvoPrincipal(userId, "EMPLOYEE", List.of(3L), Long.valueOf(userId)),
            Set.of(channel),
            message -> {
              throw new IllegalStateException("socket closed");
            }));
  }
}
