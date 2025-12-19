package com.untitleddelivery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final LocationWebSocketHandler locationWebSocketHandler;

  public WebSocketConfig(LocationWebSocketHandler locationWebSocketHandler) {
    this.locationWebSocketHandler = locationWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(locationWebSocketHandler, "/ws/locations")
        .setAllowedOrigins("*");
  }
}
