package com.zero1blog.backend.config;

import java.util.List;
import java.util.Map;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import com.zero1blog.backend.service.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fix #2 — WebSocket JWT Authentication.
 *
 * Validates a JWT passed as a query parameter (?token=...) before the WebSocket
 * handshake is accepted. If the token is missing or invalid, the handshake is
 * rejected with 401. The verified publicId is stored in the session attributes
 * so handlers can use it without trusting client-supplied data.
 *
 * Usage on the frontend: new WebSocket("wss://host/ws/chat?token=" + accessToken)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {

        List<String> tokenParams = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .get("token");

        if (tokenParams == null || tokenParams.isEmpty()) {
            log.warn("WebSocket handshake rejected: no token query parameter");
            return false;
        }

        String token = tokenParams.get(0);

        if (!jwtService.isTokenValid(token)) {
            log.warn("WebSocket handshake rejected: invalid or expired JWT");
            return false;
        }

        // Store verified identity in session — handlers must use this, never trust client messages
        String publicId = jwtService.extractPublicId(token);
        attributes.put("publicId", publicId);
        log.debug("WebSocket handshake accepted for publicId: {}", publicId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No post-handshake action needed
    }
}
