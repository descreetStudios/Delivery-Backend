package com.untitleddelivery;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import redis.embedded.RedisServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DeliveryTrackingIntegrationTest {

	private static RedisServer redisServer;
	private static final int REDIS_PORT = 6379;

	static {
		try {
			redisServer = RedisServer.builder().port(REDIS_PORT).setting("bind 127.0.0.1").build();
			redisServer.start();
			Thread.sleep(1000);
		} catch (Exception e) {
			throw new RuntimeException("Failed to start embedded Redis", e);
		}
	}

	@AfterAll
	static void stopEmbeddedRedis() {
		if (redisServer != null) {
			redisServer.stop();
		}
	}

	@LocalServerPort
	private int port;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	private final ObjectMapper mapper = new ObjectMapper();

	private String baseUrl;
	private String wsUrl;

	@BeforeEach
	void setUp() {
		baseUrl = "http://localhost:" + port;
		wsUrl = "ws://localhost:" + port + "/ws/locations";
		redisTemplate.getConnectionFactory().getConnection().flushAll();
	}

	private HttpHeaders jsonHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.set("Content-Type", "application/json");
		headers.set("Accept", "application/json");
		return headers;
	}

	private org.springframework.web.client.RestTemplate restTemplate() {
		return new org.springframework.web.client.RestTemplate();
	}

	private <T> ResponseEntity<T> postJson(String url, String body, Class<T> responseType) {
		return restTemplate().exchange(url, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), responseType);
	}

	/**
	 * Send a location update via WebSocket (the ONLY way to update location now).
	 * Returns the WebSocket session used.
	 */
	private WebSocketSession sendLocationViaWs(String courierId, double lat, double lng, double heading) throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();

		WebSocketHandler handler = new AbstractWebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				sessionRef.set(session);
				String msg = String.format(
					"{\"type\":\"location_update\",\"courierId\":\"%s\",\"latitude\":%.4f,\"longitude\":%.4f,\"heading\":%.1f,\"timestamp\":\"2026-04-03T12:00:00Z\",\"status\":\"ONLINE\"}",
					courierId, lat, lng, heading
				);
				session.sendMessage(new TextMessage(msg));
			}

			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
				// We receive the broadcast back — that means the location was stored
				latch.countDown();
			}
		};

		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketSession session = client.doHandshake(handler, wsUrl).get(5, TimeUnit.SECONDS);

		// Also subscribe to the courier so we get the broadcast back (confirmation)
		Thread.sleep(200);
		session.sendMessage(new TextMessage(String.format("{\"type\":\"subscribe\",\"courierId\":\"%s\"}", courierId)));

		latch.await(5, TimeUnit.SECONDS);
		return sessionRef.get();
	}

	// ===== REST API TESTS =====

	@Test
	@Order(1)
	void test01_getCourierLocation_returnsStoredData() throws Exception {
		// Send location via WebSocket
		WebSocketSession wsSession = sendLocationViaWs("courier001", 45.4642, 9.1900, 180.0);

		// Fetch via REST
		ResponseEntity<String> response = restTemplate().getForEntity(
			baseUrl + "/api/locations/courier/courier001", String.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode json = mapper.readTree(response.getBody());
		assertEquals("courier001", json.get("courierId").asText());
		assertEquals(45.4642, json.get("latitude").asDouble(), 0.0001);
		wsSession.close();
	}

	@Test
	@Order(2)
	void test02_getNonExistentCourier_returns404() throws Exception {
		try {
			restTemplate().getForEntity(baseUrl + "/api/locations/courier/nonexistent", String.class);
			throw new AssertionError("Expected 404 exception");
		} catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
			assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
		}
	}

	@Test
	@Order(3)
	void test03_createOrder_returns200() throws Exception {
		String body = """
			{
				"orderId": "order001",
				"pickupLatitude": 45.0703,
				"pickupLongitude": 9.4998,
				"deliveryLatitude": 45.3057,
				"deliveryLongitude": 9.4989,
				"items": [
					{"name": "Pizza", "quantity": 2, "price": 9.50},
					{"name": "Cola", "quantity": 1, "price": 2.00}
				],
				"totalPrice": 21.00
			}
			""";

		ResponseEntity<String> response = postJson(baseUrl + "/api/orders", body, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	@Order(4)
	void test04_getOrder_returnsStoredData() throws Exception {
		String createBody = """
			{
				"orderId": "order002",
				"pickupLatitude": 45.1000,
				"pickupLongitude": 9.5000,
				"deliveryLatitude": 45.3000,
				"deliveryLongitude": 9.4900,
				"items": [
					{"name": "Burger", "quantity": 1, "price": 7.50}
				],
				"totalPrice": 7.50
			}
			""";
		postJson(baseUrl + "/api/orders", createBody, String.class);

		ResponseEntity<String> response = restTemplate().getForEntity(
			baseUrl + "/api/orders/order002", String.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode json = mapper.readTree(response.getBody());
		assertEquals("order002", json.get("orderId").asText());
		assertEquals(45.1000, json.get("pickupLatitude").asDouble(), 0.0001);
		assertEquals(1, json.get("items").size());
		assertEquals("Burger", json.get("items").get(0).get("name").asText());
		assertEquals(7.50, json.get("totalPrice").asDouble(), 0.01);
	}

	@Test
	@Order(5)
	void test05_createOrderWithoutItems_returns400() throws Exception {
		String body = """
			{
				"orderId": "orderNoItems",
				"pickupLatitude": 45.0,
				"pickupLongitude": 9.5,
				"deliveryLatitude": 45.3,
				"deliveryLongitude": 9.49
			}
			""";

		try {
			postJson(baseUrl + "/api/orders", body, String.class);
			throw new AssertionError("Expected 400 exception");
		} catch (org.springframework.web.client.HttpClientErrorException.BadRequest e) {
			assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
		}
	}

	@Test
	@Order(6)
	void test06_getNonExistentOrder_returns404() throws Exception {
		try {
			restTemplate().getForEntity(baseUrl + "/api/orders/nonexistent", String.class);
			throw new AssertionError("Expected 404 exception");
		} catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
			assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
		}
	}

	@Test
	@Order(6)
	void test06_assignCourierToOrder_returns200() throws Exception {
		// Create courier location via WebSocket
		WebSocketSession wsSession = sendLocationViaWs("courier010", 45.0703, 9.4998, 0.0);

		// Create order
		String orderBody = """
			{
				"orderId": "order010",
				"pickupLatitude": 45.0703,
				"pickupLongitude": 9.4998,
				"deliveryLatitude": 45.3057,
				"deliveryLongitude": 9.4989,
				"items": [{"name": "Sushi", "quantity": 1, "price": 15.00}],
				"totalPrice": 15.00
			}
			""";
		postJson(baseUrl + "/api/orders", orderBody, String.class);

		// Assign
		ResponseEntity<String> response = restTemplate().exchange(
			baseUrl + "/api/orders/order010/assign?courierId=courier010",
			HttpMethod.PUT,
			new HttpEntity<>(jsonHeaders()),
			String.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		// Verify courier has the order
		String locationResp = restTemplate().getForEntity(
			baseUrl + "/api/locations/courier/courier010", String.class
		).getBody();
		JsonNode json = mapper.readTree(locationResp);
		assertEquals("order010", json.get("associatedOrderId").asText());

		wsSession.close();
	}

	@Test
	@Order(7)
	void test07_assignCourierToOrder_raceCondition_returns409() throws Exception {
		// Setup
		WebSocketSession wsSession = sendLocationViaWs("courier020", 45.0, 9.5, 0.0);

		String orderBody = """
			{
				"orderId": "order020",
				"pickupLatitude": 45.0,
				"pickupLongitude": 9.5,
				"deliveryLatitude": 45.3,
				"deliveryLongitude": 9.49,
				"items": [{"name": "Taco", "quantity": 3, "price": 4.00}],
				"totalPrice": 12.00
			}
			""";
		postJson(baseUrl + "/api/orders", orderBody, String.class);

		// Assign once — should succeed
		ResponseEntity<String> first = restTemplate().exchange(
			baseUrl + "/api/orders/order020/assign?courierId=courier020",
			HttpMethod.PUT,
			new HttpEntity<>(jsonHeaders()),
			String.class
		);
		assertEquals(HttpStatus.OK, first.getStatusCode());

		// Try again — should get 409 Conflict
		try {
			restTemplate().exchange(
				baseUrl + "/api/orders/order020/assign?courierId=courier020",
				HttpMethod.PUT,
				new HttpEntity<>(jsonHeaders()),
				String.class
			);
			throw new AssertionError("Expected 409 exception");
		} catch (org.springframework.web.client.HttpClientErrorException.Conflict e) {
			assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
		}

		wsSession.close();
	}

	@Test
	@Order(8)
	void test08_completeOrder_returns200() throws Exception {
		WebSocketSession wsSession = sendLocationViaWs("courier030", 45.0, 9.5, 0.0);

		String orderBody = """
			{
				"orderId": "order030",
				"pickupLatitude": 45.0,
				"pickupLongitude": 9.5,
				"deliveryLatitude": 45.3,
				"deliveryLongitude": 9.49,
				"items": [{"name": "Pasta", "quantity": 2, "price": 11.00}],
				"totalPrice": 22.00
			}
			""";
		postJson(baseUrl + "/api/orders", orderBody, String.class);

		restTemplate().exchange(
			baseUrl + "/api/orders/order030/assign?courierId=courier030",
			HttpMethod.PUT,
			new HttpEntity<>(jsonHeaders()),
			String.class
		);

		// Complete
		ResponseEntity<String> response = restTemplate().exchange(
			baseUrl + "/api/orders/order030/complete?courierId=courier030",
			HttpMethod.PUT,
			new HttpEntity<>(jsonHeaders()),
			String.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());

		String orderResp = restTemplate().getForEntity(
			baseUrl + "/api/orders/order030", String.class
		).getBody();
		JsonNode json = mapper.readTree(orderResp);
		assertEquals("COMPLETED", json.get("status").asText());

		wsSession.close();
	}

	@Test
	@Order(9)
	void test09_getActiveOrderForCourier_returnsOrder() throws Exception {
		WebSocketSession wsSession = sendLocationViaWs("courier040", 45.0, 9.5, 0.0);

		String orderBody = """
			{
				"orderId": "order040",
				"pickupLatitude": 45.0,
				"pickupLongitude": 9.5,
				"deliveryLatitude": 45.3,
				"deliveryLongitude": 9.49,
				"items": [{"name": "Salad", "quantity": 1, "price": 8.50}],
				"totalPrice": 8.50
			}
			""";
		postJson(baseUrl + "/api/orders", orderBody, String.class);

		restTemplate().exchange(
			baseUrl + "/api/orders/order040/assign?courierId=courier040",
			HttpMethod.PUT,
			new HttpEntity<>(jsonHeaders()),
			String.class
		);

		ResponseEntity<String> response = restTemplate().getForEntity(
			baseUrl + "/api/orders/courier/courier040/active", String.class
		);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode json = mapper.readTree(response.getBody());
		assertEquals("order040", json.get("orderId").asText());

		wsSession.close();
	}

	@Test
	@Order(10)
	void test10_getActiveOrderForCourier_noOrder_returns404() throws Exception {
		sendLocationViaWs("courier050", 45.0, 9.5, 0.0);

		try {
			restTemplate().getForEntity(baseUrl + "/api/orders/courier/courier050/active", String.class);
			throw new AssertionError("Expected 404 exception");
		} catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
			assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
		}
	}

	// ===== WEBSOCKET TESTS =====

	@Test
	@Order(20)
	void test20_webSocket_locationUpdateBroadcastsToSubscribers() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> receivedMessage = new AtomicReference<>();
		AtomicReference<Throwable> wsError = new AtomicReference<>();
		AtomicReference<WebSocketSession> subscriberSession = new AtomicReference<>();

		WebSocketHandler subscriberHandler = new AbstractWebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				subscriberSession.set(session);
				session.sendMessage(new TextMessage("{\"type\": \"subscribe\", \"courierId\": \"courierWS01\"}"));
			}

			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
				receivedMessage.set(message.getPayload());
				latch.countDown();
			}

			@Override
			public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
				wsError.set(exception);
			}
		};

		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketSession subscriber = client.doHandshake(subscriberHandler, wsUrl).get(5, TimeUnit.SECONDS);

		// Wait for subscription to register
		Thread.sleep(500);

		// Send location update via a SECOND WebSocket connection (the courier)
		WebSocketSession courierSession = sendLocationViaWs("courierWS01", 45.4700, 9.2000, 90.0);

		// Wait for the broadcast
		boolean received = latch.await(5, TimeUnit.SECONDS);

		assertTrue(received, "Subscriber should have received a broadcast message");
		assertNull(wsError.get(), "No WebSocket errors should have occurred");
		assertNotNull(receivedMessage.get(), "Received message should not be null");

		JsonNode json = mapper.readTree(receivedMessage.get());
		assertEquals("courierWS01", json.get("courierId").asText());
		assertEquals(45.47, json.get("latitude").asDouble(), 0.01);
		assertEquals(9.2, json.get("longitude").asDouble(), 0.01);

		subscriber.close();
		courierSession.close();
	}

	@Test
	@Order(21)
	void test21_webSocket_unsubscribe_doesNotReceiveUpdates() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);
		AtomicReference<String> receivedMessage = new AtomicReference<>();

		WebSocketHandler handler = new AbstractWebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				session.sendMessage(new TextMessage("{\"type\": \"subscribe\", \"courierId\": \"courierWS02\"}"));
				Thread.sleep(200);
				session.sendMessage(new TextMessage("{\"type\": \"unsubscribe\", \"courierId\": \"courierWS02\"}"));
			}

			@Override
			protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
				receivedMessage.set(message.getPayload());
				latch.countDown();
			}
		};

		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketSession subscriber = client.doHandshake(handler, wsUrl).get(5, TimeUnit.SECONDS);

		Thread.sleep(500);

		// Send location update
		WebSocketSession courierSession = sendLocationViaWs("courierWS02", 45.1, 9.6, 180.0);

		// Should NOT receive a message
		boolean received = latch.await(2, TimeUnit.SECONDS);
		assertEquals(false, received, "Should NOT receive a broadcast after unsubscribing");

		subscriber.close();
		courierSession.close();
	}

	@Test
	@Order(22)
	void test22_webSocket_subscribeToNonExistentCourier_noCrash() throws Exception {
		WebSocketHandler handler = new AbstractWebSocketHandler() {
			@Override
			public void afterConnectionEstablished(WebSocketSession session) throws Exception {
				session.sendMessage(new TextMessage("{\"type\": \"subscribe\", \"courierId\": \"nonexistent_courier\"}"));
			}

			@Override
			public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {}
		};

		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketSession session = assertDoesNotThrow(() ->
			client.doHandshake(handler, wsUrl).get(5, TimeUnit.SECONDS)
		);

		session.close();
	}
}
