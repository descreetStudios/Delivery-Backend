package com.untitleddelivery.controller;

import com.untitleddelivery.model.Order;
import com.untitleddelivery.service.LocationService;
import com.untitleddelivery.service.OrderQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

	private static final Logger log = LoggerFactory.getLogger(
		OrderController.class
	);

	private final LocationService locationService;
	private final OrderQueueService orderQueueService;

	public OrderController(LocationService locationService, OrderQueueService orderQueueService) {
		this.locationService = locationService;
		this.orderQueueService = orderQueueService;
	}

	@PostMapping
	public ResponseEntity<Map<String, Object>> createOrder(@RequestBody Order order) {
		try {
			if (order.getItems() == null || order.getItems().isEmpty()) {
				log.warn("Order creation rejected: no items provided");
				return ResponseEntity.badRequest().build();
			}
			locationService.createOrder(order);
			
			// Trigger auto-assignment
			boolean assigned = orderQueueService.autoAssignCourier(order);
			
			// Return order ID and assignment status
			Map<String, Object> response = new HashMap<>();
			response.put("orderId", order.getOrderId());
			response.put("assigned", assigned);
			response.put("status", assigned ? "ASSIGNED" : "QUEUED");
			
			return ResponseEntity.ok(response);
		} catch (Exception e) {
			log.error("Error creating order", e);
			return ResponseEntity.status(500).build();
		}
	}

	@GetMapping("/{orderId}")
	public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
		Order order = locationService.getOrder(orderId);
		if (order != null) {
			return ResponseEntity.ok(order);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@PutMapping("/{orderId}/assign")
	public ResponseEntity<Void> assignCourierToOrder(
		@PathVariable String orderId,
		@RequestParam String courierId
	) {
		try {
			locationService.assignCourierToOrder(orderId, courierId);
			return ResponseEntity.ok().build();
		} catch (IllegalArgumentException e) {
			log.warn("Bad request for order assignment: {}", e.getMessage());
			return ResponseEntity.badRequest().build();
		} catch (IllegalStateException e) {
			log.warn("Conflict assigning courier/order: {}", e.getMessage());
			return ResponseEntity.status(409).build();
		} catch (Exception e) {
			log.error("Error assigning courier to order", e);
			return ResponseEntity.status(500).build();
		}
	}

	@PutMapping("/{orderId}/complete")
	public ResponseEntity<Void> completeOrder(
		@PathVariable String orderId,
		@RequestParam String courierId
	) {
		try {
			locationService.completeOrder(orderId, courierId);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			log.error("Error completing order", e);
			return ResponseEntity.status(500).build();
		}
	}

	@GetMapping("/courier/{courierId}/active")
	public ResponseEntity<Order> getActiveOrderForCourier(
		@PathVariable String courierId
	) {
		Order order = locationService.getActiveOrderForCourier(courierId);
		return order != null
			? ResponseEntity.ok(order)
			: ResponseEntity.notFound().build();
	}

	@GetMapping("/queue/status")
	public ResponseEntity<Map<String, Object>> getQueueStatus() {
		Long queueSize = orderQueueService.getQueueSize();
		List<String> queuedOrders = orderQueueService.getQueueOrders();
		
		Map<String, Object> response = new HashMap<>();
		response.put("queueSize", queueSize);
		response.put("orders", queuedOrders);
		
		return ResponseEntity.ok(response);
	}

	@PostMapping("/queue/process")
	public ResponseEntity<Map<String, String>> processQueue() {
		orderQueueService.triggerQueueProcessing();
		
		Map<String, String> response = new HashMap<>();
		response.put("message", "Queue processing triggered");
		return ResponseEntity.ok(response);
	}
}
