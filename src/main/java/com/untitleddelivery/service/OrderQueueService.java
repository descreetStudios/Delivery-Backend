package com.untitleddelivery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.untitleddelivery.model.CourierLocation;
import com.untitleddelivery.model.Order;
import com.untitleddelivery.model.Location;
import com.untitleddelivery.util.DistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderQueueService {

	private static final Logger log = LoggerFactory.getLogger(OrderQueueService.class);

	private final RedisTemplate<String, Object> redisTemplate;
	private final LocationService locationService;
	private final ObjectMapper objectMapper;

	// Queue key for pending orders (FIFO)
	private static final String ORDER_QUEUE_KEY = "order:queue:pending";
	// TTL for queue entries (2 hours)
	private static final int QUEUE_TTL_MINUTES = 120;
	// Maximum queue size to prevent memory issues
	private static final int MAX_QUEUE_SIZE = 1000;

	public OrderQueueService(
		RedisTemplate<String, Object> redisTemplate,
		LocationService locationService
	) {
		this.redisTemplate = redisTemplate;
		this.locationService = locationService;

		this.objectMapper = new ObjectMapper();
		this.objectMapper.registerModule(new JavaTimeModule());
		this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// Clean up stale courier associations on startup
		clearStaleCourierAssociations();
	}

	/**
	 * Clear stale courier associations on startup.
	 * Removes associatedOrderId from all couriers where the order no longer exists.
	 */
	private void clearStaleCourierAssociations() {
		try {
			Set<String> keys = redisTemplate.keys("courier:location:*");
			if (keys == null || keys.isEmpty()) {
				return;
			}

			int cleared = 0;
			for (String key : keys) {
				Object value = redisTemplate.opsForValue().get(key);
				if (value != null) {
					CourierLocation location = objectMapper.convertValue(value, CourierLocation.class);
					if (location.getAssociatedOrderId() != null) {
						Order order = locationService.getOrder(location.getAssociatedOrderId());
						if (order == null) {
							log.info("Clearing stale associatedOrderId '{}' for courier {}", location.getAssociatedOrderId(), location.getCourierId());
							location.setAssociatedOrderId(null);
							redisTemplate.opsForValue().set(key, location, 5, TimeUnit.MINUTES);
							cleared++;
						}
					}
				}
			}
			log.info("Cleared {} stale courier associations on startup", cleared);
		} catch (Exception e) {
			log.error("Error clearing stale courier associations", e);
		}
	}

	/**
	 * Add an order to the pending queue (FIFO).
	 * @param orderId The order ID to add to queue
	 */
	public void addToQueue(String orderId) {
		try {
			// Check queue size limit
			Long queueSize = redisTemplate.opsForList().size(ORDER_QUEUE_KEY);
			if (queueSize != null && queueSize >= MAX_QUEUE_SIZE) {
				log.warn("Order queue is full ({} entries). Rejecting order {}", queueSize, orderId);
				throw new IllegalStateException("Order queue is full. Please try again later.");
			}

			// Update order status to QUEUED before adding to queue
			Order order = locationService.getOrder(orderId);
			if (order != null && !"QUEUED".equals(order.getStatus())) {
				order.setStatus("QUEUED");
				locationService.createOrder(order);
				log.info("Order {} status updated to QUEUED", orderId);
			}

			// Add to the end of the queue (right push for FIFO)
			redisTemplate.opsForList().rightPush(ORDER_QUEUE_KEY, orderId);
			// Set TTL on the queue key
			redisTemplate.expire(ORDER_QUEUE_KEY, QUEUE_TTL_MINUTES, TimeUnit.MINUTES);

			log.info("Order {} added to pending queue. Queue size: {}", orderId, getQueueSize());
		} catch (Exception e) {
			log.error("Failed to add order {} to queue", orderId, e);
			throw e;
		}
	}

	/**
	 * Get the next order from the queue (FIFO - first in, first out).
	 * @return Order ID or null if queue is empty
	 */
	public String pollFromQueue() {
		Object orderId = redisTemplate.opsForList().leftPop(ORDER_QUEUE_KEY);
		if (orderId != null) {
			log.info("Order {} polled from queue. Remaining queue size: {}", orderId, getQueueSize());
			return orderId.toString();
		}
		return null;
	}

	/**
	 * Get current queue size.
	 */
	public Long getQueueSize() {
		Long size = redisTemplate.opsForList().size(ORDER_QUEUE_KEY);
		return size != null ? size : 0L;
	}

	/**
	 * Get all orders currently in the queue.
	 */
	public List<String> getQueueOrders() {
		Long size = redisTemplate.opsForList().size(ORDER_QUEUE_KEY);
		if (size == null || size == 0) {
			return Collections.emptyList();
		}
		List<Object> orders = redisTemplate.opsForList().range(ORDER_QUEUE_KEY, 0, -1);
		return orders.stream()
			.map(Object::toString)
			.collect(Collectors.toList());
	}

	/**
	 * Remove a specific order from the queue (e.g., if cancelled).
	 * @param orderId The order to remove
	 */
	public void removeFromQueue(String orderId) {
		redisTemplate.opsForList().remove(ORDER_QUEUE_KEY, 1, orderId);
		log.info("Order {} removed from queue", orderId);
	}

	/**
	 * Get all available (idle) couriers from Redis.
	 * A courier is available if they have no associated order and status is ONLINE or IDLE.
	 * Also clears stale associatedOrderId where the order no longer exists.
	 */
	public List<CourierLocation> getAvailableCouriers() {
		try {
			// Get all courier location keys
			Set<String> keys = redisTemplate.keys("courier:location:*");
			if (keys == null || keys.isEmpty()) {
				log.debug("No courier locations found in Redis");
				return Collections.emptyList();
			}

			List<CourierLocation> availableCouriers = new ArrayList<>();
			for (String key : keys) {
				Object value = redisTemplate.opsForValue().get(key);
				if (value != null) {
					CourierLocation location = objectMapper.convertValue(value, CourierLocation.class);
					
					// Clear stale associatedOrderId where order no longer exists
					if (location.getAssociatedOrderId() != null) {
						Order order = locationService.getOrder(location.getAssociatedOrderId());
						if (order == null) {
							log.info("Clearing stale associatedOrderId for courier {}", location.getCourierId());
							location.setAssociatedOrderId(null);
							redisTemplate.opsForValue().set(key, location, 5, java.util.concurrent.TimeUnit.MINUTES);
						}
					}
					
					// Check if courier is available (no order assigned and status is ONLINE or IDLE)
					if (location.getAssociatedOrderId() == null &&
					    (location.getStatus() == null ||
					     location.getStatus().equals("ONLINE") ||
					     location.getStatus().equals("IDLE"))) {
						availableCouriers.add(location);
					}
				}
			}

			log.debug("Found {} available couriers", availableCouriers.size());
			return availableCouriers;
		} catch (Exception e) {
			log.error("Error retrieving available couriers", e);
			return Collections.emptyList();
		}
	}

	public CourierLocation findClosestAvailableCourier(Location restaurant) {
		List<CourierLocation> availableCouriers = getAvailableCouriers();

		if (availableCouriers.isEmpty()) {
			log.info("No available couriers found for pickup location ({}, {})",
				restaurant.getLatitude(), restaurant.getLongitude());
			return null;
		}

		// Find the closest courier using Haversine distance
		CourierLocation closestCourier = null;
		double minDistance = Double.MAX_VALUE;

		for (CourierLocation courier : availableCouriers) {
			double distance = DistanceCalculator.calculateDistance(
				restaurant.getLatitude(), restaurant.getLongitude(),
				courier.getLatitude(), courier.getLongitude()
			);

			if (distance < minDistance) {
				minDistance = distance;
				closestCourier = courier;
			}
		}

		if (closestCourier != null) {
			log.info("Found closest courier {} at distance {:.2f} km", closestCourier.getCourierId(), minDistance);
		}

		return closestCourier;
	}

	/**
	 * Automatically assign the closest available courier to an order.
	 * If no couriers are available, add the order to the queue.
	 * @param order The order to assign
	 * @return true if courier was assigned, false if queued
	 */
	public boolean autoAssignCourier(Order order) {
		log.info("Attempting to auto-assign courier for order {}", order.getOrderId());

		CourierLocation closestCourier = findClosestAvailableCourier(
			order.getRestaurant()
		);

		if (closestCourier != null) {
			// Assign the courier
			try {
				locationService.assignCourierToOrder(order.getOrderId(), closestCourier.getCourierId());
				log.info("Successfully assigned courier {} to order {}", closestCourier.getCourierId(), order.getOrderId());
				return true;
			} catch (Exception e) {
				log.error("Failed to assign courier {} to order {}: {}",
					closestCourier.getCourierId(), order.getOrderId(), e.getMessage());
				// If assignment fails, add to queue
				addToQueue(order.getOrderId());
				return false;
			}
		} else {
			// No couriers available, add to queue
			addToQueue(order.getOrderId());
			log.info("No couriers available. Order {} added to queue", order.getOrderId());
			return false;
		}
	}

	/**
	 * Process the order queue and assign orders to available couriers.
	 * This method is called periodically to handle FIFO queue processing.
	 */
	@Scheduled(fixedRate = 5000) // Run every 5 seconds
	public void processOrderQueue() {
		try {
			Long queueSize = getQueueSize();
			if (queueSize == 0) {
				return; // Nothing to process
			}

			log.info("Processing order queue. Current size: {}", queueSize);

			// Process orders in FIFO order
			String orderId = pollFromQueue();
			while (orderId != null) {
				Order order = locationService.getOrder(orderId);
				if (order == null) {
					log.warn("Order {} not found, skipping", orderId);
					orderId = pollFromQueue();
					continue;
				}

				// Try to find an available courier
				CourierLocation closestCourier = findClosestAvailableCourier(
					order.getRestaurant()
				);

				if (closestCourier != null) {
					// Assign the courier
					try {
						locationService.assignCourierToOrder(orderId, closestCourier.getCourierId());
						log.info("Queue: Successfully assigned courier {} to order {}",
							closestCourier.getCourierId(), orderId);
						// Successfully assigned, move to next order
						orderId = pollFromQueue();
					} catch (Exception e) {
						log.error("Queue: Failed to assign courier to order {}: {}", orderId, e.getMessage());
						// Re-add to queue if assignment fails
						addToQueue(orderId);
						// Move to next order to avoid infinite loop
						orderId = pollFromQueue();
					}
				} else {
					// No couriers available, put order back and stop processing
					log.info("No couriers available. Re-queuing order {} and stopping processing", orderId);
					addToQueue(orderId);
					break;
				}
			}
		} catch (Exception e) {
			log.error("Error processing order queue", e);
		}
	}

	/**
	 * Manually trigger queue processing (for testing or API endpoint).
	 */
	public void triggerQueueProcessing() {
		processOrderQueue();
	}

	/**
	 * Update order status to DELIVERING when courier confirms pickup.
	 * @param orderId The order ID
	 */
	public void updateOrderToDelivering(String orderId) {
		locationService.updateOrderToDelivering(orderId);
	}
}
