package com.untitleddelivery.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.time.Instant;

public class CourierLocation implements Serializable {

	private String courierId;
	private Location location;
	private double heading;
	private Instant timestamp;
	private String status;
	private String associatedOrderId; // Optional, most likely we'll just use associatedCourierId

	public CourierLocation() {}

	@JsonCreator
	public CourierLocation(
		@JsonProperty("courierId") String courierId,
		@JsonProperty("latitude") double latitude,
		@JsonProperty("longitude") double longitude,
		@JsonProperty("heading") double heading,
		@JsonProperty("timestamp") Instant timestamp,
		@JsonProperty("status") String status,
		@JsonProperty("associatedOrderId") String associatedOrderId
	) {
		this.courierId = courierId;
		this.location = new Location(latitude, longitude);
		this.heading = heading;
		this.timestamp = timestamp;
		this.status = status;
		this.associatedOrderId = associatedOrderId;
	}

	public String getCourierId() {
		return courierId;
	}

	public Location getLocation() {
		return location;
	}

	public double getLatitude() {
		return location != null ? location.getLatitude() : 0.0;
	}

	public double getLongitude() {
		return location != null ? location.getLongitude() : 0.0;
	}

	public String getAssociatedOrderId() {
		return associatedOrderId;
	}

	public void setAssociatedOrderId(String associatedOrderId) {
		this.associatedOrderId = associatedOrderId;
	}

	public double getHeading() {
		return heading;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public String getStatus() {
		return status;
	}

	public void setCourierId(String courierId) {
		this.courierId = courierId;
	}

	@JsonProperty("latitude")
	public void setLatitude(double latitude) {
		if (location != null) {
			location.setLatitude(latitude);
		} else {
			this.location = new Location(latitude, getLongitude());
		}
	}

	@JsonProperty("longitude")
	public void setLongitude(double longitude) {
		if (location != null) {
			location.setLongitude(longitude);
		} else if (this.location == null) {
			this.location = new Location(getLatitude(), longitude);
		}
	}

	public void setHeading(double heading) {
		this.heading = heading;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setLocation(Location location) {
		this.location = location;
	}
}
