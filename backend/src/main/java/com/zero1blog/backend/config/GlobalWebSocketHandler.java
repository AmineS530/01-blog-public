package com.zero1blog.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class GlobalWebSocketHandler extends TextWebSocketHandler {

    private static final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        activeSessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (node.has("type") && "REGISTER".equals(node.get("type").asText())) {
                String publicId = node.get("publicId").asText();
                userSessions.put(publicId, session);
                session.getAttributes().put("publicId", publicId);
            }
        } catch (Exception e) {
            // Ignore malformed text messages
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        activeSessions.remove(session);
        String publicId = (String) session.getAttributes().get("publicId");
        if (publicId != null) {
            userSessions.remove(publicId);
        }
    }

    public static void broadcast(String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            for (WebSocketSession session : activeSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {
                        // Suppress connection-specific transport errors
                    }
                }
            }
        }
    }

    public static void sendToUser(String recipientPublicId, String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            WebSocketSession session = userSessions.get(recipientPublicId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    // Suppress transport errors
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
