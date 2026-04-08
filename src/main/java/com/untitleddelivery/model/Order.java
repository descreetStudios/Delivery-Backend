package com.untitleddelivery.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {
    private String orderId;
    private double[] restaurant = new double[2];
    private double[] destination = new double[2];
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
        this.restaurant[0] = pickupLatitude;
        this.restaurant[1] = pickupLongitude;
        this.destination[0] = deliveryLatitude;
        this.destination[1] = deliveryLongitude;
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
        return this.restaurant[0];
    }

    public void setPickupLatitude(double pickupLatitude) {
        this.restaurant[0] = pickupLatitude;
    }

    public double getPickupLongitude() {
        return this.restaurant[1];
    }

    public void setPickupLongitude(double pickupLongitude) {
        this.restaurant[1] = pickupLongitude;
    }

        public double getDeliveryLatitude() {
        return this.destination[0];
    }

    public void setDeliveryLatitude(double deliveryLatitude) {
        this.destination[0] = deliveryLatitude;
    }

    public double getDeliveryLongitude() {
        return this.destination[1];
    }

    public void setDeliveryLongitude(double deliveryLongitude) {
        this.destination[1] = deliveryLongitude;
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
