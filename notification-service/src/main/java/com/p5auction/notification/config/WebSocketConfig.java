package com.p5auction.notification.config;

import com.p5auction.notification.service.SubscriptionHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final SubscriptionHub hub;
    private final String origin;
    public WebSocketConfig(SubscriptionHub hub,@Value("${app.cors-origin}") String origin) {this.hub=hub;this.origin=origin;}
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub,"/ws").setAllowedOrigins(origin);
    }
}
