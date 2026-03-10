package com.untitleddelivery.controller;

import com.untitleddelivery.model.CourierLocation;
import com.untitleddelivery.model.OrderItem;
import com.untitleddelivery.service.LocationService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
@CrossOrigin(origins = "*")
public class LocationController {

	private final LocationService locationService;

	public LocationController(LocationService locationService) {
		this.locationService = locationService;
	}

	@PostMapping("/update")
	public ResponseEntity<Void> updateLocation(
		@RequestBody CourierLocation location
	) {
		locationService.updateCourierLocation(location);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/courier/{courierId}")
	public ResponseEntity<CourierLocation> getCourierLocation(
		@PathVariable String courierId
	) {
		CourierLocation location = locationService.getCourierLocation(
			courierId
		);
		return location != null
			? ResponseEntity.ok(location)
			: ResponseEntity.notFound().build();
	}

	@GetMapping("/courier/{courierId}/with-order")
	public ResponseEntity<Object> getCourierLocationWithOrder(
		@PathVariable String courierId
	) {
		CourierLocation location = locationService.getCourierLocation(
			courierId
		);
		if (location == null) {
			return ResponseEntity.notFound().build();
		}

		// Create sample order data
		List<OrderItem> orderItems = Arrays.asList(
			new OrderItem("Pizza", 2),
			new OrderItem("Burger", 1),
			new OrderItem("Salad", 3)
		);

		// Create response object with both location and order
		return ResponseEntity.ok(
			Map.of("location", location, "order", orderItems)
		);
	}
}
