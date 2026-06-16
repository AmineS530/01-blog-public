package com.zero1blog.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Dedicated event coordinator and WebSocket session manager for chats.
 * <p>
 * Manages chat session registrations (supporting multiple concurrent sessions per user),
 * user-targeted unicasting/multicasting, and online status changes.
 * </p>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketSession>> chatUserSessions = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<WebSocketSession> chatActiveSessions = new CopyOnWriteArraySet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        chatActiveSessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (node.has("type") && "REGISTER".equals(node.get("type").asText())) {
                String publicId = node.get("publicId").asText();
                session.getAttributes().put("publicId", publicId);
                
                boolean isFirstSession = false;
                synchronized (chatUserSessions) {
                    CopyOnWriteArraySet<WebSocketSession> sessions = chatUserSessions.computeIfAbsent(publicId, k -> new CopyOnWriteArraySet<>());
                    if (sessions.isEmpty()) {
                        isFirstSession = true;
                    }
                    sessions.add(session);
                }
                
                if (isFirstSession) {
                    broadcast("USER_ONLINE", Map.of("publicId", publicId));
                }
            }
        } catch (Exception e) {
            // Ignore malformed text payloads
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        chatActiveSessions.remove(session);
        String publicId = (String) session.getAttributes().get("publicId");
        if (publicId != null) {
            boolean isLastSession = false;
            synchronized (chatUserSessions) {
                CopyOnWriteArraySet<WebSocketSession> sessions = chatUserSessions.get(publicId);
                if (sessions != null) {
                    sessions.remove(session);
                    if (sessions.isEmpty()) {
                        chatUserSessions.remove(publicId);
                        isLastSession = true;
                    }
                }
            }
            if (isLastSession) {
                broadcast("USER_OFFLINE", Map.of("publicId", publicId));
            }
        }
    }

    public static void broadcast(String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            for (WebSocketSession session : chatActiveSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {
                        // Suppress connection-specific transport failures
                    }
                }
            }
        }
    }

    public static void sendToUser(String recipientPublicId, String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            CopyOnWriteArraySet<WebSocketSession> sessions = chatUserSessions.get(recipientPublicId);
            if (sessions != null) {
                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(new TextMessage(payload));
                        } catch (Exception e) {
                            // Suppress single-user transport failures
                        }
                    }
                }
            }
        }
    }

    public static Set<String> getOnlineUsers() {
        return chatUserSessions.keySet();
    }

    private static String createPayload(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            return null;
        }
    }
}
