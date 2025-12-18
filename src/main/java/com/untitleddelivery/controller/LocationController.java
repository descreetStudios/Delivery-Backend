package com.untitleddelivery.controller;

import com.untitleddelivery.model.CourierLocation;
import com.untitleddelivery.service.LocationService;
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
  public ResponseEntity<Void> updateLocation(@RequestBody CourierLocation location) {
    locationService.updateCourierLocation(location);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/courier/{courierId}")
  public ResponseEntity<CourierLocation> getCourierLocation(@PathVariable String courierId) {
    CourierLocation location = locationService.getCourierLocation(courierId);
    return location != null ? ResponseEntity.ok(location) : ResponseEntity.notFound().build();
  }
}
