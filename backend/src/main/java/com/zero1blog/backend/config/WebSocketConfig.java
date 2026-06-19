package com.zero1blog.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSockets infrastructure configuration.
 *
 * Both endpoints require a valid JWT via the JwtHandshakeInterceptor before the
 * connection is accepted. Allowed origins are driven by config so localhost is
 * available in dev and the real domain is locked in production.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GlobalWebSocketHandler globalWebSocketHandler;
    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    // Fix #2: drive allowed origins from config — set to production domain in env
    @Value("${websocket.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    public WebSocketConfig(GlobalWebSocketHandler globalWebSocketHandler,
                           ChatWebSocketHandler chatWebSocketHandler,
                           JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.globalWebSocketHandler = globalWebSocketHandler;
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(globalWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)   // Fix #2: reject unauthenticated connections
                .setAllowedOrigins(allowedOrigins);

        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins(allowedOrigins);
    }
}
