package com.untitleddelivery.controller;

import com.untitleddelivery.model.Order;
import com.untitleddelivery.service.LocationService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

	private static final Logger log = LoggerFactory.getLogger(
		OrderController.class
	);

	private final LocationService locationService;

	public OrderController(LocationService locationService) {
		this.locationService = locationService;
	}

	@PostMapping
	public ResponseEntity<Void> createOrder(@RequestBody Order order) {
		try {
			locationService.createOrder(order);
			return ResponseEntity.ok().build();
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
		if (order != null) {
			return ResponseEntity.ok(order);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/courier/{courierId}")
	public ResponseEntity<Order> getOrderByCourier(
		@PathVariable String courierId
	) {
		Order order = locationService.getActiveOrderForCourier(courierId);
		if (order != null) {
			return ResponseEntity.ok(order);
		} else {
			return ResponseEntity.notFound().build();
		}
	}
}
