package com.untitleddelivery.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Order implements Serializable {

    private String orderId;
    private Location restaurant;
    private Location destination;
    private String associatedCourierId;
    private Instant createdAt;
    private String status; // QUEUED, FETCHING, DELIVERING, COMPLETED
    private List<OrderItem> items;
    private double totalPrice;

    public Order() {}

    public Order(
        Location restaurant,
        Location destination,
        ArrayList<OrderItem> orderItems,
        double totalPrice
    ) {
        this.orderId = String.valueOf(Instant.now().toEpochMilli());
        this.restaurant = restaurant;
        this.destination = destination;
        this.createdAt = Instant.now();
        this.status = "QUEUED";
        this.items = new ArrayList<>();
        this.totalPrice = totalPrice;
    }

    public String getOrderId() {
        return this.orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public Location getRestaurant() {
        return this.restaurant;
    }

    public void setRestaurant(Location restaurant) {
        this.restaurant = restaurant;
    }

    public Location getDestination() {
        return this.destination;
    }

    public void setDestination(Location destination) {
        this.destination = destination;
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
