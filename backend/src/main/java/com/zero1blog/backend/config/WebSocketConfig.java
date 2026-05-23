package com.zero1blog.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Spring WebSockets infrastructure configuration.
 * <p>
 * This class implements {@link WebSocketConfigurer} to bootstrap the WebSocket communication channel,
 * map connection routing, and inject the global handler.
 * </p>
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GlobalWebSocketHandler globalWebSocketHandler;

    /**
     * Instantiates WebSocketConfig with the central event coordinator.
     *
     * @param globalWebSocketHandler the central session and broadcast handler.
     */
    public WebSocketConfig(GlobalWebSocketHandler globalWebSocketHandler) {
        this.globalWebSocketHandler = globalWebSocketHandler;
    }

    /**
     * Registers the raw WebSocket endpoint and maps it to our custom handler.
     * <p>
     * Connects the incoming URL route {@code /ws} directly to the handler instance.
     * Sets Allowed Origins to {@code "*"} to enable seamless developer testing and multi-origin frontends 
     * (e.g., Angular dev server on {@code http://localhost:4200}).
     * </p>
     *
     * @param registry registers and structures WebSocket handler mappings.
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(globalWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
    }
}
