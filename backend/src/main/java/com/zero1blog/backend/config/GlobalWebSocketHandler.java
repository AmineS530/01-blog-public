package com.zero1blog.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * General event coordinator for public system-wide WebSocket broadcasts.
 * <p>
 * Handles publishing post releases and like triggers globally to all active client connections.
 * </p>
 */
@Component
public class GlobalWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArraySet<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        activeSessions.add(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // General socket handles broadcasts from server only, client sends no message frames here.
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        activeSessions.remove(session);
    }

    public static void broadcast(String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            for (WebSocketSession session : activeSessions) {
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

    private static String createPayload(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            return null;
        }
    }
}
