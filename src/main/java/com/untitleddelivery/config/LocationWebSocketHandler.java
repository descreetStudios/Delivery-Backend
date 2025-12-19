package com.untitleddelivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LocationWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(LocationWebSocketHandler.class);
  private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper;

  public LocationWebSocketHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.put(session.getId(), session);
    log.info("✅ WebSocket connection established: {} (Total connections: {})",
        session.getId(), sessions.size());
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session.getId());
    log.info("🔌 WebSocket connection closed: {} (Total connections: {})",
        session.getId(), sessions.size());
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    log.debug("Received message from {}: {}", session.getId(), message.getPayload());
  }

  public void broadcast(Object message) {
    try {
      String jsonMessage = objectMapper.writeValueAsString(message);
      log.info("📡 Broadcasting to {} client(s): {}", sessions.size(), jsonMessage);

      sessions.values().forEach(session -> {
        try {
          if (session.isOpen()) {
            session.sendMessage(new TextMessage(jsonMessage));
            log.debug("✅ Sent to session: {}", session.getId());
          }
        } catch (IOException e) {
          log.error("❌ Error sending message to session {}: {}",
              session.getId(), e.getMessage());
        }
      });
    } catch (Exception e) {
      log.error("❌ Error broadcasting message: {}", e.getMessage());
    }
  }
}
