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

/**
 * Real-time event coordinator and WebSocket session manager.
 * <p>
 * This class serves as the core real-time bridge between the Spring Boot backend and the
 * Angular client application. It manages active socket connections, parses incoming system 
 * frames (e.g., registration signals), and exposes thread-safe endpoints for broadcasting 
 * blog updates (posts, comments, likes) or routing target-specific chat messages.
 * </p>
 */
@Component
public class GlobalWebSocketHandler extends TextWebSocketHandler {

    /**
     * A thread-safe lookup map mapping a user's unique {@code publicId} to their open WebSocket session.
     * Utilized for direct user-to-user routing such as real-time messaging, ensuring low-latency retrieval
     * without compromising thread-safety in highly concurrent multi-threaded environments.
     */
    private static final ConcurrentHashMap<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /**
     * A thread-safe collection of all active, open WebSocket sessions regardless of whether the user
     * has registered their public ID yet. 
     * <p>
     * {@link CopyOnWriteArraySet} is selected here because updates (session join/leave events) are relatively
     * rare compared to high-frequency iteration reads (broadcasting new posts, comments, likes). This guarantees
     * thread-safe traversal during broad broadcasts without explicit lock synchronization.
     * </p>
     */
    private static final CopyOnWriteArraySet<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();

    /**
     * JSON Mapper configured with {@link JavaTimeModule} to support robust serialization and deserialization
     * of modern Java 8 Date/Time types (e.g., Instant, LocalDateTime) transmitted over socket payloads.
     */
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * Callback triggered when a new WebSocket handshake is successfully established.
     * Automatically registers the newly opened session to the global broadcast pool.
     *
     * @param session the newly opened WebSocket session.
     * @throws Exception if post-processing fails.
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        activeSessions.add(session);
    }

    /**
     * Intercepts and parses text payloads transmitted from the client.
     * <p>
     * Primary responsibility is decoding incoming registration commands ({@code REGISTER}). When a client
     * successfully logs in, they send a register frame containing their {@code publicId}, which is subsequently
     * bound to the session attributes and stored in the targeted {@code userSessions} lookup table.
     * </p>
     *
     * @param session the originating WebSocket session.
     * @param message the received raw text message.
     * @throws Exception if message processing fails.
     */
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
            // Ignore malformed text payloads to keep connection robust
        }
    }

    /**
     * Clean-up callback executed immediately after a WebSocket connection is closed.
     * Removes the stale session from the global broadcast pool and purges its registered association
     * from the targeted lookup map to prevent memory leaks and dangling session holds.
     *
     * @param session the closed WebSocket session.
     * @param status  the close status criteria.
     * @throws Exception if cleanup handling fails.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        activeSessions.remove(session);
        String publicId = (String) session.getAttributes().get("publicId");
        if (publicId != null) {
            userSessions.remove(publicId);
        }
    }

    /**
     * Broadcasts a JSON serialized payload containing an event {@code type} and custom payload {@code data}
     * to every single active WebSocket connection currently open.
     * <p>
     * Iterates over {@code activeSessions} safely. If a session is closed or encounters a transport-level error,
     * the error is caught and suppressed locally to prevent a single faulty connection from interrupting
     * broadcasts to the rest of the application ecosystem.
     * </p>
     *
     * @param type the event classification string (e.g., "NEW_POST", "NEW_COMMENT").
     * @param data the model entity or payload to be serialized.
     */
    public static void broadcast(String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            for (WebSocketSession session : activeSessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {
                        // Suppress connection-specific transport failures to preserve broadcast continuity
                    }
                }
            }
        }
    }

    /**
     * Transmits a directed event payload directly to a specific user's active WebSocket connection.
     * <p>
     * Looks up the user's active session from {@code userSessions}. If the target user is currently offline
     * or disconnected, the call terminates gracefully, meaning real-time events are delivered best-effort, and
     * full historic state synchronizations are fallback-resolved via REST API queries.
     * </p>
     *
     * @param recipientPublicId the public identifier of the target user.
     * @param type              the event classification string (e.g., "NEW_MESSAGE").
     * @param data              the model entity or payload to be serialized.
     */
    public static void sendToUser(String recipientPublicId, String type, Object data) {
        String payload = createPayload(type, data);
        if (payload != null) {
            WebSocketSession session = userSessions.get(recipientPublicId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    // Suppress single-user transport failures gracefully
                }
            }
        }
    }

    /**
     * Serializes a structured key-value payload containing "type" and "data" properties into a JSON string.
     *
     * @param type event code.
     * @param data event entity details.
     * @return the serialized JSON string, or {@code null} if serialization fails.
     */
    private static String createPayload(String type, Object data) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            return null;
        }
    }
}
