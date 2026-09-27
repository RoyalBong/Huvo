package com.huvo.notify.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.huvo.notify.domain.Notification;
import com.huvo.notify.service.NotificationService;
import com.huvo.notify.websocket.Channel;
import com.huvo.notify.websocket.WebSocketHub;
import com.huvo.security.HuvoPrincipal;

import lombok.RequiredArgsConstructor;

/**
 * The notification feed API.
 *
 * <p>Everything here is scoped to the caller's own {@code userId} from the token. There is no path
 * parameter for whose feed to read, deliberately: an endpoint that takes an id invites the one bug
 * this API most needs to be unable to have, which is showing one employee another's attendance and
 * leave.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

  private final NotificationService notifications;
  private final WebSocketHub hub;

  /**
   * The caller's own feed, newest first.
   *
   * @param principal the caller
   * @param limit how many to return, bounded
   * @return the feed
   */
  @GetMapping
  public List<Notification> mine(
      @AuthenticationPrincipal HuvoPrincipal principal,
      @RequestParam(defaultValue = "50") int limit) {
    // Bounded so one parameter cannot ask for the whole table.
    int bounded = Math.max(1, Math.min(limit, 200));
    return notifications.recent(principal.userId(), bounded);
  }

  /**
   * The channels a client may subscribe to.
   *
   * <p>Served over REST as well as being implicit in the socket protocol, so the frontend can
   * render its channel list before opening the connection and can detect a backend that does not
   * yet speak a channel it knows about.
   *
   * @return every channel's wire name
   */
  @GetMapping("/channels")
  public List<String> channels() {
    return Channel.allWireNames();
  }
}
