package com.untitleddelivery.model;

import java.io.Serializable;
import java.time.Instant;

public class CourierLocation implements Serializable {
  private String courierId;
  private double latitude;
  private double longitude;
  private double heading;
  private Instant timestamp;
  private String status;

  public CourierLocation() {
  }

  public CourierLocation(String courierId, double latitude, double longitude,
      double accuracy, double speed, double heading,
      Instant timestamp, String status) {
    this.courierId = courierId;
    this.latitude = latitude;
    this.longitude = longitude;
    this.heading = heading;
    this.timestamp = timestamp;
    this.status = status;
  }

  public String getCourierId() {
    return courierId;
  }

  public double getLatitude() {
    return latitude;
  }

  public double getLongitude() {
    return longitude;
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

  public void setLatitude(double latitude) {
    this.latitude = latitude;
  }

  public void setLongitude(double longitude) {
    this.longitude = longitude;
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
}
