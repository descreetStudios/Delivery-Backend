package com.untitleddelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.untitleddelivery.config.LocationWebSocketHandler;
import com.untitleddelivery.model.CourierLocation;
import com.untitleddelivery.model.Order;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

	private static final Logger log = LoggerFactory.getLogger(
		LocationService.class
	);
	private final RedisTemplate<String, Object> redisTemplate;
	private final LocationWebSocketHandler webSocketHandler;
	private static final String LOCATION_KEY_PREFIX = "courier:location:";
	private static final int LOCATION_TTL_MINUTES = 5;

	// Order-related keys
	private static final String ORDER_KEY_PREFIX = "order:";
	private static final int ORDER_TTL_MINUTES = 60;

	private final ObjectMapper objectMapper;

	public LocationService(
		RedisTemplate<String, Object> redisTemplate,
		LocationWebSocketHandler webSocketHandler
	) {
		this.redisTemplate = redisTemplate;
		this.webSocketHandler = webSocketHandler;

		// Configure ObjectMapper for Instant serialization/deserialization
		this.objectMapper = new ObjectMapper();
		this.objectMapper.registerModule(new JavaTimeModule());
		this.objectMapper.disable(
			SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
		);
	}

	public void updateCourierLocation(CourierLocation location) {
		String key = LOCATION_KEY_PREFIX + location.getCourierId();

		log.info("Updating location for courier: {}", location.getCourierId());

		// Store in Valkey
		redisTemplate
			.opsForValue()
			.set(key, location, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

		log.debug("Location stored in Valkey: {}", key);

		// Broadcast to WebSocket clients
		webSocketHandler.broadcast(location);

		log.info("📡 Location broadcasted via WebSocket");
	}

	public CourierLocation getCourierLocation(String courierId) {
		String key = LOCATION_KEY_PREFIX + courierId;

		log.info("Fetching location for courier: {}", courierId);

		Object value = redisTemplate.opsForValue().get(key);
		if (value == null) {
			log.warn("No location found for courier: {}", courierId);
			return null;
		}

		// Convert the raw Object to CourierLocation using ObjectMapper
		CourierLocation location = objectMapper.convertValue(
			value,
			CourierLocation.class
		);

		log.debug("Location found for courier: {}", courierId);
		return location;
	}

	public void createOrder(Order order) {
		String key = ORDER_KEY_PREFIX + order.getOrderId();

		log.info("Creating order: {}", order.getOrderId());

		// Store the order
		redisTemplate
			.opsForValue()
			.set(key, order, ORDER_TTL_MINUTES, TimeUnit.MINUTES);

		log.info("Order created successfully: {}", order.getOrderId());
	}

	public Order getOrder(String orderId) {
		String key = ORDER_KEY_PREFIX + orderId;

		log.info("Fetching order: {}", orderId);

		Object value = redisTemplate.opsForValue().get(key);
		if (value == null) {
			log.warn("No order found with ID: {}", orderId);
			return null;
		}

		// Convert to Order object using ObjectMapper
		return objectMapper.convertValue(value, Order.class);
	}

	public void assignCourierToOrder(String orderId, String courierId) {
		// Check if the courier already has an order assigned
		CourierLocation existingLocation = getCourierLocation(courierId);
		if (
			existingLocation != null &&
			existingLocation.getAssociatedOrderId() != null
		) {
			log.warn(
				"Courier {} already has order {} assigned",
				courierId,
				existingLocation.getAssociatedOrderId()
			);
			return;
		}

		Order order = getOrder(orderId);
		if (order == null) {
			log.warn("Cannot assign courier - order not found: {}", orderId);
			return;
		}

		// Update the order with the courier
		order.setAssociatedCourierId(courierId);
		createOrder(order);

		// Update the courier's location record with the order ID
		CourierLocation location = getCourierLocation(courierId);
		if (location != null) {
			location.setAssociatedOrderId(orderId);
			updateCourierLocation(location);
		}

		log.info("Assigned courier {} to order {}", courierId, orderId);
	}

	public void completeOrder(String orderId, String courierId) {
		Order order = getOrder(orderId);
		if (order != null) {
			order.setStatus("COMPLETED");
			createOrder(order);
		}

		// Remove the order from the courier's location record
		CourierLocation location = getCourierLocation(courierId);
		if (location != null) {
			location.setAssociatedOrderId(null);
			updateCourierLocation(location);
		}

		log.info("Completed order {}", orderId);
	}

	public Order getActiveOrderForCourier(String courierId) {
		// Get the courier's current location
		CourierLocation location = getCourierLocation(courierId);
		if (location == null || location.getAssociatedOrderId() == null) {
			return null;
		}

		// Return the active order for this courier
		return getOrder(location.getAssociatedOrderId());
	}
}
