package com.huvo.notify.websocket;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import com.huvo.security.HuvoPrincipal;
import com.huvo.security.HuvoSecurityException;
import com.huvo.security.HuvoTokenService;

import lombok.RequiredArgsConstructor;

/**
 * Registers the single socket at {@code /ws/notify} and authenticates the handshake.
 *
 * <p>A browser cannot set an {@code Authorization} header on a WebSocket upgrade, so the token
 * arrives as a query parameter and is validated in-process with {@link HuvoTokenService} - the same
 * code that validates every REST call, so there is one definition of a valid token rather than two.
 *
 * <p>Rejection happens at the handshake, returning 401 before the socket is established. Validating
 * after the connection is up would mean an accepted-then-closed socket, which the frontend's
 * exponential-backoff reconnect would immediately retry against.
 *
 * <p>{@code allowedOrigins} is permissive because Section 8.1 runs everything on one host and Nginx
 * fronts it; the browser's own same-origin policy is the real control, and a token is still
 * required. Tightening this needs the deployed origin, which does not exist yet.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

  private final NotifyWebSocketHandler handler;
  private final HuvoTokenService tokens;

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(handler, "/ws/notify")
        .addInterceptors(new TokenHandshakeInterceptor(tokens))
        .setAllowedOriginPatterns("*");
  }

  /**
   * Validates the {@code token} query parameter and stashes the principal on the session.
   *
   * <p>The token is never logged, only the reason it was rejected.
   */
  static class TokenHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenHandshakeInterceptor.class);

    private static final String TOKEN_PARAM = "token";

    private final HuvoTokenService tokens;

    TokenHandshakeInterceptor(HuvoTokenService tokens) {
      this.tokens = tokens;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes) {
      String token = tokenFrom(request);
      if (token == null) {
        log.debug("Rejecting a WebSocket handshake with no token");
        return false;
      }
      try {
        HuvoPrincipal principal = tokens.validateAccessToken(token);
        attributes.put(NotifyWebSocketHandler.PRINCIPAL_ATTRIBUTE, principal);
        return true;
      } catch (HuvoSecurityException e) {
        // The reason, never the token.
        log.debug("Rejecting a WebSocket handshake: {}", e.getMessage());
        return false;
      }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception) {
      // Nothing to do: the decision was made before the handshake, and the handler reads the
      // principal straight off the session attributes.
    }

    private String tokenFrom(ServerHttpRequest request) {
      if (!(request instanceof ServletServerHttpRequest servletRequest)) {
        return null;
      }
      String value = servletRequest.getServletRequest().getParameter(TOKEN_PARAM);
      return value == null || value.isBlank() ? null : value.trim();
    }
  }
}
