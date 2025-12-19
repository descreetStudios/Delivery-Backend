package com.untitleddelivery.service;

import com.untitleddelivery.config.LocationWebSocketHandler;
import com.untitleddelivery.model.CourierLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.concurrent.TimeUnit;

@Service
public class LocationService {

  private static final Logger log = LoggerFactory.getLogger(LocationService.class);
  private final RedisTemplate<String, Object> redisTemplate;
  private final LocationWebSocketHandler webSocketHandler;
  private static final String LOCATION_KEY_PREFIX = "courier:location:";
  private static final int LOCATION_TTL_MINUTES = 5;

  private final ObjectMapper objectMapper;

  public LocationService(RedisTemplate<String, Object> redisTemplate,
      LocationWebSocketHandler webSocketHandler) {
    this.redisTemplate = redisTemplate;
    this.webSocketHandler = webSocketHandler;

    // Configure ObjectMapper for Instant serialization/deserialization
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }

  public void updateCourierLocation(CourierLocation location) {
    String key = LOCATION_KEY_PREFIX + location.getCourierId();

    log.info("Updating location for courier: {}", location.getCourierId());

    // Store in Valkey
    redisTemplate.opsForValue().set(key, location, LOCATION_TTL_MINUTES, TimeUnit.MINUTES);

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
    CourierLocation location = objectMapper.convertValue(value, CourierLocation.class);

    log.debug("Location found for courier: {}", courierId);
    return location;
  }
}
