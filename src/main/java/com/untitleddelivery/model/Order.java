package com.untitleddelivery.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {
    private String orderId;
    private double pickupLatitude;
    private double pickupLongitude;
    private double deliveryLatitude;
    private double deliveryLongitude;
    private String associatedCourierId;
    private Instant createdAt;
    private String status; // PENDING, ASSIGNED, DELIVERING, COMPLETED, CANCELLED
    private List<OrderItem> items;
    private double totalPrice;

    public Order() {
    }

    public Order(double pickupLatitude, double pickupLongitude,
            double deliveryLatitude, double deliveryLongitude, ArrayList<OrderItem> orderItems, double totalPrice) {
        this.orderId = String.valueOf(Instant.now().toEpochMilli());
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.deliveryLatitude = deliveryLatitude;
        this.deliveryLongitude = deliveryLongitude;
        this.createdAt = Instant.now();
        this.status = "PENDING";
        this.items = new ArrayList<>();
        this.totalPrice = totalPrice;
    }

    public String getOrderId() {
        return this.orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public double getPickupLatitude() {
        return this.pickupLatitude;
    }

    public void setPickupLatitude(double pickupLatitude) {
        this.pickupLatitude = pickupLatitude;
    }

    public double getPickupLongitude() {
        return this.pickupLongitude;
    }

    public void setPickupLongitude(double pickupLongitude) {
        this.pickupLongitude = pickupLongitude;
    }

    public double getDeliveryLatitude() {
        return this.deliveryLatitude;
    }

    public void setDeliveryLatitude(double deliveryLatitude) {
        this.deliveryLatitude = deliveryLatitude;
    }

    public double getDeliveryLongitude() {
        return this.deliveryLongitude;
    }

    public void setDeliveryLongitude(double deliveryLongitude) {
        this.deliveryLongitude = deliveryLongitude;
    }

    public String getAssociatedCourierId() {
        return this.associatedCourierId;
    }

    public void setAssociatedCourierId(String associatedCourierId) {
        this.associatedCourierId = associatedCourierId;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return this.items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public double getTotalPrice() {
        return this.totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}
