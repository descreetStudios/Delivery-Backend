package com.untitleddelivery.model;

import java.io.Serializable;
import java.time.Instant;

public class Order implements Serializable {
    private String orderId;
    private double pickupLatitude;
    private double pickupLongitude;
    private double deliveryLatitude;
    private double deliveryLongitude;
    private String associatedCourierId;
    private Instant createdAt;
    private String status; // PENDING, ASSIGNED, DELIVERING, COMPLETED, CANCELLED

    public Order() {
    }

    public Order(String orderId, double pickupLatitude, double pickupLongitude,
                 double deliveryLatitude, double deliveryLongitude) {
        this.orderId = orderId;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.createdAt = Instant.now();
        this.status = "PENDING";
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public double getPickupLatitude() {
        return pickupLatitude;
    }

    public void setPickupLatitude(double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public double getPickupLongitude() {
        return pickupLongitude;
    }

    public void setPickupLongitude(double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public double getDeliveryLatitude() {
        return deliveryLatitude;
    }

    public void setDeliveryLatitude(double deliveryLatitude) {
        this.deliveryLatitude = deliveryLatitude;
    }

    public double getDeliveryLongitude() {
        return deliveryLongitude;
    }

    public void setDeliveryLongitude(double deliveryLongitude) {
        this.deliveryLongitude = deliveryLongitude;
    }

    public String getAssociatedCourierId() {
        return associatedCourierId;
    }

    public void setAssociatedCourierId(String associatedCourierId) {
        this.associatedCourierId = associatedCourierId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
