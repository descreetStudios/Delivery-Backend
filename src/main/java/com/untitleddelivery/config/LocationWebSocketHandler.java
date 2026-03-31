package com.untitleddelivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class LocationWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(
		LocationWebSocketHandler.class
	);

	// Session ID -> Set of subscribed Courier IDs
	private final Map<String, Set<String>> sessionSubscriptions =
		new ConcurrentHashMap<>();

	// Courier ID -> Set of session IDs subscribed to that courier
	private final Map<String, Set<String>> courierSubscribers =
		new ConcurrentHashMap<>();

	// Session ID -> WebSocketSession object
	private final Map<String, WebSocketSession> sessions =
		new ConcurrentHashMap<>();

	private final ObjectMapper objectMapper;

	public LocationWebSocketHandler(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessionSubscriptions.put(
			session.getId(),
			ConcurrentHashMap.newKeySet()
		);
		sessions.put(session.getId(), session);
		log.info(
			"✅ WebSocket connection established: {} (Total connections: {})",
			session.getId(),
			sessionSubscriptions.size()
		);
	}

	@Override
	public void afterConnectionClosed(
		WebSocketSession session,
		CloseStatus status
	) {
		// Remove all subscriptions for this session
		Set<String> subscribedCouriers = sessionSubscriptions.remove(
			session.getId()
		);
		if (subscribedCouriers != null) {
			subscribedCouriers.forEach(courierId ->
				removeSubscriberFromCourier(courierId, session.getId())
			);
		}

		// Remove the session from our sessions map
		sessions.remove(session.getId());

		log.info(
			"🔌 WebSocket connection closed: {} (Total connections: {})",
			session.getId(),
			sessionSubscriptions.size()
		);
	}

	@Override
	protected void handleTextMessage(
		WebSocketSession session,
		TextMessage message
	) {
		String payload = message.getPayload();
		log.debug("Received message from {}: {}", session.getId(), payload);

		try {
			// Parse the incoming message to determine its type
			Map<String, Object> messageMap = objectMapper.readValue(
				payload,
				Map.class
			);
			String type = (String) messageMap.get("type");

			if ("subscribe".equals(type)) {
				handleSubscribe(session, messageMap);
			} else if ("unsubscribe".equals(type)) {
				handleUnsubscribe(session, messageMap);
			} else {
				log.warn("Unknown message type: {}", type);
			}
		} catch (Exception e) {
			log.error(
				"Error processing message from {}: {}",
				session.getId(),
				e.getMessage()
			);
		}
	}

	private void handleSubscribe(
		WebSocketSession session,
		Map<String, Object> messageMap
	) {
		String courierId = (String) messageMap.get("courierId");

		if (courierId == null || courierId.trim().isEmpty()) {
			log.warn(
				"Subscribe request missing courierId from session: {}",
				session.getId()
			);
			return;
		}

		Set<String> subscriptions = sessionSubscriptions.computeIfAbsent(
			session.getId(),
			id -> ConcurrentHashMap.newKeySet()
		);

		if (subscriptions.add(courierId)) {
			// Add session to the courier's subscriber list
			Set<String> subscribers = courierSubscribers.computeIfAbsent(
				courierId,
				id -> ConcurrentHashMap.newKeySet()
			);
			subscribers.add(session.getId());

			log.info(
				"✅ Client {} subscribed to courier: {}",
				session.getId(),
				courierId
			);
		}
	}

	private void handleUnsubscribe(
		WebSocketSession session,
		Map<String, Object> messageMap
	) {
		String courierId = (String) messageMap.get("courierId");

		if (courierId == null || courierId.trim().isEmpty()) {
			return;
		}

		Set<String> subscriptions = sessionSubscriptions.get(session.getId());
		if (subscriptions != null && subscriptions.remove(courierId)) {
			// Remove session from the courier's subscriber list
			removeSubscriberFromCourier(courierId, session.getId());

			log.info(
				"✅ Client {} unsubscribed from courier: {}",
				session.getId(),
				courierId
			);
		}
	}

	private void removeSubscriberFromCourier(
		String courierId,
		String sessionId
	) {
		Set<String> subscribers = courierSubscribers.get(courierId);
		if (subscribers != null) {
			subscribers.remove(sessionId);

			// Clean up empty subscriber lists
			if (subscribers.isEmpty()) {
				courierSubscribers.remove(courierId);
			}
		}
	}

	/**
	 * Broadcast location update to clients subscribed to a specific courier
	 */
	public void broadcastToCourier(String courierId, Object locationData) {
		Set<String> subscriberIds = courierSubscribers.get(courierId);

		if (subscriberIds == null || subscriberIds.isEmpty()) {
			log.debug("No subscribers for courier: {}", courierId);
			return;
		}

		try {
			String jsonMessage = objectMapper.writeValueAsString(locationData);
			log.info(
				"📡 Broadcasting location update for courier {} to {} client(s)",
				courierId,
				subscriberIds.size()
			);

			for (String sessionId : subscriberIds) {
				WebSocketSession session = sessions.get(sessionId);

				if (session != null && session.isOpen()) {
					try {
						session.sendMessage(new TextMessage(jsonMessage));
						log.debug(
							"✅ Sent location update to session: {} for courier: {}",
							sessionId,
							courierId
						);
					} catch (IOException e) {
						log.error(
							"❌ Error sending message to session {}: {}",
							sessionId,
							e.getMessage()
						);
					}
				}
			}
		} catch (Exception e) {
			log.error(
				"❌ Error broadcasting location update for courier {}: {}",
				courierId,
				e.getMessage()
			);
		}
	}

	/**
	 * Get the count of active subscriptions across all couriers
	 */
	public int getTotalSubscriptions() {
		return courierSubscribers.values().stream().mapToInt(Set::size).sum();
	}

	/**
	 * Broadcast location update to all subscribed clients (for backward compatibility)
	 */
	public void broadcast(Object locationData) {
		// For now, we'll just log that this method is being called
		// The actual implementation would depend on how you want to broadcast
		// This method might be called from LocationService but isn't fully implemented yet
		log.info("Broadcast called with data: {}", locationData);
	}
}
