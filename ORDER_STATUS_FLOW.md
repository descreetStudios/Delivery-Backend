# Order Status Flow Implementation

## Overview
This document describes the complete order status flow implemented in the delivery backend system.

## Status Transitions

```
┌─────────────┐
│   PENDING   │ ← Order created (default status)
└──────┬──────┘
       │
       ▼
┌─────────────┐    When order added to queue
│   QUEUED    │ ───────────────────────────
└──────┬──────┘    (no available courier)
       │
       ▼
┌─────────────┐    Courier assigned to order
│  FETCHING   │ ───────────────────────────
└──────┬──────┘    (going to pickup location)
       │
       ▼
┌─────────────┐    Courier picks up order
│  DELIVERING │ ───────────────────────────
└──────┬──────┘    (en route to destination)
       │
       ▼
┌─────────────┐
│  DELIVERED  │ ← Order delivered to customer
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ COMPLETED   │ ← Final completion state
└─────────────┘
```

## Status Definitions

| Status | Description | When Applied |
|--------|-------------|--------------|
| **PENDING** | Order created, waiting for assignment | Order creation |
| **QUEUED** | Order in queue, no courier available | Added to queue |
| **FETCHING** | Courier assigned, going to pickup | Courier assignment |
| **DELIVERING** | Courier has picked up, en route | Pickup confirmed |
| **DELIVERED** | Order delivered to customer | Delivery completion |
| **COMPLETED** | Order fully completed | Final completion |

## Implementation Details

### Backend Endpoints

#### 1. Create Order
```
POST /api/orders
```
- Creates order with status `PENDING`
- Automatically assigns courier if available
- Returns current status in response

#### 2. Get Order
```
GET /api/orders/{orderId}
```
- Returns complete order details including current status

#### 3. Assign Courier
```
PUT /api/orders/{orderId}/assign?courierId={courierId}
```
- Assigns courier to order
- Updates status to `FETCHING`
- Adds order to courier's active order

#### 4. Update to Delivering
```
PUT /api/orders/{orderId}/delivering
```
- Updates status to `DELIVERING`
- Called when courier confirms pickup

#### 5. Complete Order
```
PUT /api/orders/{orderId}/complete?courierId={courierId}
```
- Updates status to `DELIVERED` (if was DELIVERING)
- Updates status to `COMPLETED` (final state)
- Clears courier's active order

#### 6. Queue Status
```
GET /api/orders/queue/status
```
- Returns current queue size and order IDs

#### 7. Process Queue
```
POST /api/orders/queue/process
```
- Manually triggers queue processing
- Assigns queued orders to available couriers

### Status Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    ORDER LIFECYCLE FLOW                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌─────────┐                                                   │
│   │ CREATE  │ ────────────────────────────────────────────┐    │
│   └────┬────┘                                             │    │
│        │                                                  │    │
│        ▼                                                  │    │
│   ┌─────────┐    ┌──────────────────────────────────┐    │    │
│   │PENDING  │────│  Auto-assign / Queue Processing  │    │    │
│   └────┬────┘    └──────────────────────────────────┘    │    │
│        │                                                  │    │
│   ┌────┴────┐     ┌──────────────────────────────────┐   │    │
│   │Status   │────▶│  If courier available:           │   │    │
│   │Updated  │     │  - Assign courier                │   │    │
│   │to:      │     │  - Set status: FETCHING          │   │    │
│   │QUEUED   │     │  If no courier:                  │   │    │
│   │         │     │  - Add to queue                  │   │    │
│   └────┬────┘     │  - Set status: QUEUED            │   │    │
│        │          └──────────────────────────────────┘   │    │
│   ┌────┴────┐                                             │    │
│   │Courier  │────▶  Courier Location Update              │    │
│   │Assigned │     (WebSocket)                            │    │
│   └────┬────┘                                             │    │
│        │                                                  │    │
│   ┌────┴────┐     ┌──────────────────────────────────┐   │    │
│   │Status   │────▶│  When courier reaches pickup:    │   │    │
│   │Updated  │     │  - Call /delivering endpoint     │   │    │
│   │to:      │     │  - Set status: DELIVERING        │   │    │
│   │FETCHING │     └──────────────────────────────────┘   │    │
│   └────┬────┘                                             │    │
│        │                                                  │    │
│   ┌────┴────┐     ┌──────────────────────────────────┐   │    │
│   │Status   │────▶│  When delivery completed:        │   │    │
│   │Updated  │     │  - Call /complete endpoint       │   │    │
│   │to:      │     │  - Set status: DELIVERED         │   │    │
│   │DELIVERED│     │  - Final: Set status: COMPLETED  │   │    │
│   └────┬────┘     └──────────────────────────────────┘   │    │
│        │                                                  │    │
│   ┌────┴────┐                                             │    │
│   │Status   │────▶  Courier cleared, order archived      │    │
│   │Updated  │                                              │    │
│   │to:      │                                             │    │
│   │COMPLETED│                                             │    │
│   └─────────┘                                             │    │
│                                                           │    │
└───────────────────────────────────────────────────────────┴────┘
```

### Key Files Modified

| File | Changes |
|------|---------|
| `Order.java` | Updated comment to include COMPLETED status |
| `LocationService.java` | Added status transition logic, updated `createOrder()`, `assignCourierToOrder()`, `completeOrder()`, added `updateOrderToDelivering()`, `updateOrderToQueued()` |
| `OrderController.java` | Updated response status, added `/delivering` endpoint |
| `OrderQueueService.java` | Added status update when adding to queue, added `updateOrderToDelivering()` wrapper |
| `useOrdersApi.js` | Added `updateOrderToDelivering()` method |
| `useOrderDataApi.js` | Added `updateOrderToDelivering()` method |

### State Machine Summary

```
CURRENT STATE    EVENT              NEXT STATE
─────────────────────────────────────────────────────────
PENDING          Assign Courier     FETCHING
PENDING          No Courier Available QUEUED
QUEUED           Courier Available  FETCHING
FETCHING         Pickup Confirmed   DELIVERING
DELIVERING       Delivery Complete  DELIVERED
DELIVERED        Finalize           COMPLETED
```

### API Usage Examples

#### 1. Create Order
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "restaurant": {"latitude": 45.30240, "longitude": 9.48550},
    "destination": {"latitude": 45.31240, "longitude": 9.49550},
    "items": [{"name": "Pizza", "quantity": 2, "price": 12.99}],
    "totalPrice": 25.98
  }'

# Response:
# {
#   "orderId": "1713262200000",
#   "assigned": true,
#   "status": "FETCHING"  // or "QUEUED" if no courier available
# }
```

#### 2. Assign Courier Manually
```bash
curl -X PUT "http://localhost:8080/api/orders/1713262200000/assign?courierId=courier1"
```

#### 3. Update to Delivering (Pickup Confirmed)
```bash
curl -X PUT http://localhost:8080/api/orders/1713262200000/delivering
```

#### 4. Complete Order
```bash
curl -X PUT "http://localhost:8080/api/orders/1713262200000/complete?courierId=courier1"
```

#### 5. Get Queue Status
```bash
curl http://localhost:8080/api/orders/queue/status
```
