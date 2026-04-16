package com.untitleddelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.untitleddelivery.config.LocationWebSocketHandler;
import com.untitleddelivery.model.CourierLocation;
import com.untitleddelivery.model.Order;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LocationService {

	private static final Logger log = LoggerFactory.getLogger(
		LocationService.class);
	private final RedisTemplate<String, Object> redisTemplate;
	private final LocationWebSocketHandler webSocketHandler;
	private final OrderQueueService orderQueueService;
	private static final String LOCATION_KEY_PREFIX = "courier:location:";
	private static final int LOCATION_TTL_MINUTES = 5;

	// Order-related keys
	private static final String ORDER_KEY_PREFIX = "order:";
	private static final int ORDER_TTL_MINUTES = 60;

	// Distributed lock keys for atomic courier-order assignment
	private static final String ASSIGNMENT_LOCK_COURIER_PREFIX = "courier:lock:assignment:";
	private static final String ASSIGNMENT_LOCK_ORDER_PREFIX = "order:lock:assignment:";
	private static final int ASSIGNMENT_LOCK_TTL_SECONDS = 10;

	private final ObjectMapper objectMapper;

	public LocationService(
		RedisTemplate<String, Object> redisTemplate,
		LocationWebSocketHandler webSocketHandler,
		@Lazy OrderQueueService orderQueueService
	) {
		this.redisTemplate = redisTemplate;
		this.webSocketHandler = webSocketHandler;
		this.orderQueueService = orderQueueService;

		// Configure ObjectMapper for Instant serialization/deserialization
		this.objectMapper = new ObjectMapper();
		this.objectMapper.registerModule(new JavaTimeModule());
		this.objectMapper.disable(
			SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
	}

	public void updateCourierLocation(CourierLocation location) {
		String key = LOCATION_KEY_PREFIX + location.getCourierId();

		log.info("Updating location for courier: {}", location.getCourierId());

		// Check if courier was delivering and is now idle (completed an order)
		CourierLocation previousLocation = getCourierLocation(location.getCourierId());
		boolean courierBecameAvailable = false;
		if (previousLocation != null && previousLocation.getAssociatedOrderId() != null 
		    && location.getAssociatedOrderId() == null) {
			courierBecameAvailable = true;
			log.info("Courier {} became available after completing order", location.getCourierId());
		}
		// Also check if courier is new (no previous location)
		if (previousLocation == null) {
			courierBecameAvailable = true;
			log.info("New courier {} detected, triggering queue processing", location.getCourierId());
		}

		// Store in Redis
		redisTemplate
			.opsForValue()
			.set(key, location, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

		log.debug("Location stored in Redis: {}", key);

		// Broadcast to WebSocket clients
		webSocketHandler.broadcast(location);

		log.info("Location broadcasted via WebSocket");

		// Trigger queue processing if courier became available
		if (courierBecameAvailable && orderQueueService != null) {
			log.info("Triggering order queue processing for courier {}", location.getCourierId());
			orderQueueService.triggerQueueProcessing();
		}
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
			CourierLocation.class);

		log.debug("Location found for courier: {}", courierId);
		return location;
	}

	public void createOrder(Order order) {
		if (order.getOrderId() == null) {
			order.setOrderId(String.valueOf(Instant.now().toEpochMilli()));
			order.setCreatedAt(Instant.now());
			// Status defaults to QUEUED if not already set
			if (order.getStatus() == null) {
				order.setStatus("QUEUED");
			}
		}
		String key = ORDER_KEY_PREFIX + order.getOrderId();

		log.info("Creating order: {} with status: {}", order.getOrderId(), order.getStatus());

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
		String courierLockKey = ASSIGNMENT_LOCK_COURIER_PREFIX + courierId;
		String orderLockKey = ASSIGNMENT_LOCK_ORDER_PREFIX + orderId;

		// Atomically acquire lock on courier (SETNX with TTL)
		Boolean courierLocked = redisTemplate
			.opsForValue()
			.setIfAbsent(courierLockKey, "1", ASSIGNMENT_LOCK_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

		if (Boolean.FALSE.equals(courierLocked)) {
			log.warn("Courier {} is already being assigned to another order", courierId);
			throw new IllegalStateException("Courier " + courierId + " is already being assigned");
		}

		try {
			// Atomically acquire lock on order (SETNX with TTL)
			Boolean orderLocked = redisTemplate
				.opsForValue()
				.setIfAbsent(orderLockKey, "1", ASSIGNMENT_LOCK_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

			if (Boolean.FALSE.equals(orderLocked)) {
				log.warn("Order {} is already being assigned to another courier", orderId);
				throw new IllegalStateException("Order " + orderId + " is already being assigned");
			}

			try {
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
					throw new IllegalStateException("Courier " + courierId + " already has order " + existingLocation.getAssociatedOrderId() + " assigned");
				}

				Order order = getOrder(orderId);
				if (order == null) {
					log.warn("Cannot assign courier - order not found: {}", orderId);
					throw new IllegalArgumentException("Order not found: " + orderId);
				}

				if (order.getAssociatedCourierId() != null) {
					log.warn("Order {} already assigned to courier {}", orderId, order.getAssociatedCourierId());
					throw new IllegalStateException("Order " + orderId + " already assigned to courier " + order.getAssociatedCourierId());
				}

				// Update the order with the courier and status
				order.setAssociatedCourierId(courierId);
				order.setStatus("FETCHING");
				createOrder(order);

				// Update the courier's location record with the order ID
				CourierLocation location = getCourierLocation(courierId);
				if (location != null) {
					location.setAssociatedOrderId(orderId);
					updateCourierLocation(location);
				}

				log.info("Assigned courier {} to order {} with status FETCHING", courierId, orderId);
			} finally {
				// Release order lock
				redisTemplate.delete(orderLockKey);
			}
		} finally {
			// Release courier lock
			redisTemplate.delete(courierLockKey);
		}
	}

	public void completeOrder(String orderId, String courierId) {
		Order order = getOrder(orderId);
		if (order != null) {
			log.info("Completing order {} with current status: {}", orderId, order.getStatus());
			order.setStatus("COMPLETED");
			createOrder(order);
			log.info("Order {} status updated to COMPLETED", orderId);
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

	/**
	 * Update order status to DELIVERING when courier has picked up the order.
	 * This should be called when the courier confirms pickup at the restaurant.
	 * @param orderId The order ID
	 */
	public void updateOrderToDelivering(String orderId) {
		Order order = getOrder(orderId);
		if (order != null) {
			// Only transition from FETCHING to DELIVERING
			if ("FETCHING".equals(order.getStatus())) {
				order.setStatus("DELIVERING");
				createOrder(order);
				log.info("Order {} status updated to DELIVERING", orderId);
			} else {
				log.warn("Cannot transition order {} to DELIVERING from status: {}", orderId, order.getStatus());
			}
		}
	}

	/**
	 * Update order status to QUEUED when it's added to the queue.
	 * @param orderId The order ID
	 */
	public void updateOrderToQueued(String orderId) {
		Order order = getOrder(orderId);
		if (order != null) {
			order.setStatus("QUEUED");
			createOrder(order);
			log.info("Order {} status updated to QUEUED", orderId);
		}
	}
}
