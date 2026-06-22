package com.zero1blog.backend.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zero1blog.backend.dto.PostResponse;

/**
 * General event coordinator for public system-wide WebSocket broadcasts.
 * <p>
 * Handles publishing post releases and like triggers globally to all active
 * client connections,
 * plus user-targeted pushes (e.g. notifications) to just the relevant
 * session(s).
 * </p>
 */
@Component
public class GlobalWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArraySet<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();
    // publicId -> sessions (a user may have multiple tabs/devices open)
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        activeSessions.add(session);
        // publicId is already verified by JwtHandshakeInterceptor during handshake
        String publicId = (String) session.getAttributes().get("publicId");
        if (publicId != null) {
            userSessions.computeIfAbsent(publicId, k -> new CopyOnWriteArraySet<>()).add(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // General socket handles broadcasts from server only, client sends no message
        // frames here.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        activeSessions.remove(session);
        String publicId = (String) session.getAttributes().get("publicId");
        if (publicId != null) {
            CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(publicId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(publicId);
                }
            }
        }
    }

    /**
     * Fix #12: listen for domain events and broadcast — PostService no longer calls
     * this directly.
     */
    @EventListener
    public void onPostCreated(PostCreatedEvent event) {
        PostResponse post = event.getPost();
        broadcast("NEW_POST", post, post.getAuthorPublicId());
    }

    public static void broadcast(String type, Object data, String excludedPublicId) {
        String payload = createPayload(type, data);

        if (payload != null) {
            for (WebSocketSession session : activeSessions) {
                String publicId = (String) session.getAttributes().get("publicId");

                if (session.isOpen() &&
                        !excludedPublicId.equals(publicId)) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {

                    }
                }
            }
        }
    }

    public static void broadcast(String type, Object data) {
        String payload = createPayload(type, data);

        if (payload != null) {
            for (WebSocketSession session : activeSessions) {
                String publicId = (String) session.getAttributes().get("publicId");

                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {

                    }
                }
            }
        }

    }

    /**
     * Sends a payload only to the given user's open session(s), e.g. for live
     * notifications.
     */
    public static void sendToUser(String publicId, String type, Object data) {
        CopyOnWriteArraySet<WebSocketSession> sessions = userSessions.get(publicId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = createPayload(type, data);
        if (payload == null) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    // Suppress connection-specific transport failures
                }
            }
        }
    }

    private static String createPayload(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            return null;
        }
    }
}