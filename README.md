# Booking Service

> **Core rental workflow engine** for the Car Rental System (CRS).  
> Manages the complete lifecycle of a car rental — from booking creation and payment through vehicle handover, active trips, and trip completion.

---

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Configuration](#configuration)
  - [Running the Service](#running-the-service)
- [Booking Lifecycle](#booking-lifecycle)
- [API Reference](#api-reference)
  - [Bookings](#bookings-apiv1bookings)
  - [Driver Profiles](#driver-profiles-apiv1drivers)
  - [Payments](#payments-apiv1payments)
  - [Admin Dashboard](#admin-dashboard-apiv1dashboard)
- [Data Models](#data-models)
  - [Entities](#entities)
  - [Enums](#enums)
- [Inter-Service Communication](#inter-service-communication)
- [Environment Variables](#environment-variables)

---

## Overview

The Booking Service is the **operational core** of the Car Rental System. It orchestrates the entire rental process:

- **Booking creation** — Customers select vehicles and time ranges; the service validates availability and calculates the total amount
- **Invoice management** — A `RENTAL` invoice is automatically generated upon booking creation for immediate payment
- **Staff workflow** — Staff confirm bookings, assign drivers (for with-driver trips), and manage vehicle handover protocols
- **Driver workflow** — Drivers confirm pickups and complete trips
- **Handover protocols** — Immutable records of vehicle condition (odometer, photos, condition notes) at both pickup and return
- **Dashboard analytics** — Revenue, trip counts, and chart data aggregated for the admin panel

This service does **not** handle authentication. It trusts the JWT forwarded by the API Gateway and calls `iam-service` via Feign for user identity resolution.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.4.2 |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Service Calls | Spring Cloud OpenFeign 2024.0.0 |
| API Docs | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| Serialization | Jackson JSR310 (Java 8 date/time support) |
| Build | Maven |

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                  Booking Service (:8082)                     │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐ │
│  │  /bookings   │  │  /drivers    │  │ /payments          │ │
│  │  /dashboard  │  │              │  │ /vehicles (proxy)  │ │
│  └──────┬───────┘  └──────┬───────┘  └────────┬───────────┘ │
│         │                 │                   │             │
│  ┌──────▼─────────────────▼───────────────────▼──────────┐  │
│  │                   Service Layer                       │  │
│  │  RentalGroupService │ DriverProfileService            │  │
│  │  PaymentService     │ DashboardService                │  │
│  └────────────────────────────────┬───────────────────────┘  │
│                                   │                         │
│  ┌────────────────────────────────▼───────────────────────┐  │
│  │              Repository Layer (JPA)                    │  │
│  └────────────────────────────────┬───────────────────────┘  │
│                                   │                         │
└───────────────────────────────────┼─────────────────────────┘
                                    │
                        ┌───────────▼───────────┐
                        │      PostgreSQL        │
                        │  booking_service_db    │
                        └───────────────────────┘

External Feign Calls (direct, bypassing gateway):
  → iam-service:8080       /internal/users/{userId}
  → car-management:8081    /api/v1/vehicles/{id}
```

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 15+
- `iam-service` running on port 8080
- `car-management` running on port 8081

### Configuration

Create the database:

```sql
CREATE DATABASE booking_service_db;
```

Key settings in `src/main/resources/application.yml`:

```yaml
server:
  port: 8082

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/booking_service_db
    username: <your_db_user>
    password: <your_db_password>
  jpa:
    hibernate:
      ddl-auto: update   # Creates/updates tables automatically

feign:
  iam-service:
    url: http://localhost:8080
  car-management:
    url: http://localhost:8081
```

### Running the Service

```bash
cd booking-service

mvn spring-boot:run
```

- **Swagger UI**: http://localhost:8082/swagger-ui/index.html
- **OpenAPI JSON**: http://localhost:8082/v3/api-docs

---

## Booking Lifecycle

A rental passes through a well-defined state machine:

```
         [Customer]                 [Staff]                   [Driver / Staff]
              │                       │                              │
   POST /bookings                     │                              │
        │                             │                              │
        ▼                             │                              │
   ┌─────────┐                        │                              │
   │ PENDING │ ◄──────────────────────┤                              │
   └────┬────┘   (waiting for staff)  │                              │
        │                             │                              │
        │                  PATCH /bookings/{id}/confirm              │
        │                             │                              │
        ▼                             ▼                              │
   ┌───────────┐                      │                              │
   │ CONFIRMED │                      │                              │
   └─────┬─────┘                      │                              │
         │              PATCH /bookings/{id}/staff-handover-start    │
         │              (no-driver trips)                            │
         │              OR                                           │
         │              driver-pickup-confirmed (driver trips)       │
         ▼                                                           ▼
   ┌─────────────┐                                                   │
   │ IN_PROGRESS │ ◄─────────────────────────────────────────────────┘
   └──────┬──────┘
          │           PATCH /bookings/{id}/staff-handover-return
          │           (no-driver) OR driver-complete-trip (driver)
          ▼
   ┌───────────┐
   │ COMPLETED │
   └───────────┘

   ┌───────────┐  ← Can be reached from PENDING or CONFIRMED
   │ CANCELLED │
   └───────────┘
```

### Booking Flow by Actor

| Step | Actor | Endpoint | Notes |
|---|---|---|---|
| 1 | Customer | `POST /bookings` | Creates booking + RENTAL invoice (status: PENDING) |
| 2 | Customer | `POST /payments/process` | Pays the invoice immediately after booking |
| 3 | Staff | `PATCH /bookings/{id}/assign-driver` | For with-driver bookings only |
| 4 | Staff | `PATCH /bookings/{id}/confirm` | Confirms booking → CONFIRMED |
| 5a | Staff | `PATCH /bookings/{id}/staff-handover-start` | Self-drive: staff hands car to customer → IN_PROGRESS |
| 5b | Driver | `PATCH /bookings/{id}/driver-pickup-confirmed` | Driver trip: driver picks up customer → IN_PROGRESS |
| 6a | Staff | `PATCH /bookings/{id}/staff-handover-return` | Self-drive: staff receives car back → COMPLETED |
| 6b | Driver | `PATCH /bookings/{id}/driver-complete-trip` | Driver trip: driver completes trip → COMPLETED |

---

## API Reference

> **Base path through API Gateway**: `http://localhost:8888/api/v1`  
> **Direct base path**: `http://localhost:8082/api/v1`

### Bookings `/api/v1/bookings`

#### Create a Booking

```
POST /api/v1/bookings
```

Creates a new booking in `PENDING` status and automatically generates a `RENTAL` invoice for payment.

**Request body:**
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "deliveryMode": "SELF_PICKUP",
  "deliveryAddress": null,
  "rentalUnits": [
    {
      "vehicleId": 3,
      "isWithDriver": false,
      "startTime": "2025-06-01T08:00:00",
      "endTime": "2025-06-03T08:00:00",
      "unitPrice": 500000
    }
  ]
}
```

**`deliveryMode`**: `SELF_PICKUP` | `DELIVERY`  
**`deliveryAddress`**: Required only when `deliveryMode = DELIVERY`

**Response** includes the full booking with the newly created invoice:
```json
{
  "code": 1000,
  "data": {
    "id": 42,
    "bookingCode": "BK-20250601-0001",
    "status": "PENDING",
    "totalAmount": 1000000,
    "deliveryMode": "SELF_PICKUP",
    "rentalUnits": [...],
    "invoices": [
      {
        "id": 7,
        "type": "RENTAL",
        "amount": 1000000,
        "status": "UNPAID",
        "paidAt": null
      }
    ],
    "createdAt": "2025-06-01T07:45:00"
  }
}
```

#### List & Query Bookings

| Method | Path | Query Params | Description |
|--------|------|---|-------------|
| `GET` | `/bookings` | `status`, `page`, `size` | All bookings, paginated, optional status filter |
| `GET` | `/bookings/{id}` | — | Single booking by ID |
| `GET` | `/bookings/code/{bookingCode}` | — | Single booking by booking code (e.g. `BK-20250601-0001`) |
| `GET` | `/bookings/user/{userId}` | `page`, `size` | All bookings belonging to a user |

**Status filter values**: `PENDING` | `CONFIRMED` | `IN_PROGRESS` | `COMPLETED` | `CANCELLED` | `OVERDUE`

#### Booking State Transitions

| Method | Path | Actor | Description |
|--------|------|---|-------------|
| `PATCH` | `/bookings/{id}/cancel` | Customer/Staff | Cancel. Query param: `reason` (optional string) |
| `PATCH` | `/bookings/{id}/assign-driver` | Staff | Assign a driver to a rental unit |
| `PATCH` | `/bookings/{id}/confirm` | Staff | Confirm booking (all drivers must be assigned first for with-driver trips) |
| `PATCH` | `/bookings/{id}/staff-handover-start` | Staff | Record vehicle handover to customer (self-drive). Body: handover data |
| `PATCH` | `/bookings/{id}/staff-handover-return` | Staff | Record vehicle return from customer (self-drive). Body: handover data |
| `PATCH` | `/bookings/{id}/driver-pickup-confirmed` | Driver | Driver confirms customer pickup |
| `PATCH` | `/bookings/{id}/driver-complete-trip` | Driver | Driver marks trip as completed |

**Handover request body (for handover endpoints):**
```json
{
  "rentalUnitId": 5,
  "type": "PICKUP",
  "odoMeter": 12500,
  "condition": "Good condition, minor scratch on front bumper",
  "photos": ["url1.jpg", "url2.jpg"]
}
```

#### Available Drivers

```
GET /api/v1/bookings/available-drivers
```

Returns drivers who have `ACTIVE` status and no currently active bookings.

---

### Driver Profiles `/api/v1/drivers`

Manages the professional driver records (linked to IAM users who have the `DRIVER` role).

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/drivers` | List all drivers (merged with IAM profile data) |
| `GET` | `/drivers/{id}` | Get driver by local profile ID |
| `GET` | `/drivers/by-user/{userId}` | Get driver by IAM user UUID |
| `POST` | `/drivers` | Register a driver profile. Query params: `userId`, `licenseNumber`, `currentLocation` |
| `PUT` | `/drivers/{id}` | Update driver's license number and/or location |
| `PATCH` | `/drivers/{id}/status` | Update driver status. Query param: `status` = `ACTIVE` / `INACTIVE` / `BLOCKED` |
| `GET` | `/drivers/{driverId}/bookings` | Get a driver's booking history. Query params: `status`, `page`, `size` |

---

### Payments `/api/v1/payments`

#### Process a Payment

```
POST /api/v1/payments/process
```

Marks an invoice as paid using the specified payment method.

**Request body:**
```json
{
  "invoiceId": 7,
  "paymentMethodType": "CASH",
  "amount": 1000000
}
```

**`paymentMethodType`**: `CASH` | `BANK_TRANSFER` | `CREDIT_CARD` | `E_WALLET`

**Response:**
```json
{
  "code": 1000,
  "data": {
    "id": 7,
    "type": "RENTAL",
    "amount": 1000000,
    "status": "PAID",
    "paidAt": "2025-06-01T07:50:22",
    "paymentMethodType": "CASH"
  }
}
```

> **Note**: `amount` in the request must match `invoice.amount`. A mismatch will return a validation error.

---

### Admin Dashboard `/api/v1/dashboard`

#### Overview Statistics

```
GET /api/v1/dashboard/overview?period=7d
```

| Parameter | Options | Description |
|---|---|---|
| `period` | `7d` (default), `30d`, `all` | Time window for current period |

**Period logic:**
- `7d` → compares last 7 days vs previous 7 days (day 7–14)
- `30d` → compares last 30 days vs previous 30 days (day 30–60)
- `all` → total from beginning of time, no comparison (changePercent = 0)

**Response:**
```json
{
  "data": {
    "totalRevenue": 24500000,
    "totalTrips": 12,
    "revenueChangePercent": -15.3,
    "tripsChangePercent": 20.0,
    "pendingBookings": 3
  }
}
```

#### Chart Data

```
GET /api/v1/dashboard/chart?period=7d
```

- `7d` → 7 data points, one per day (labels: T2, T3, T4, T5, T6, T7, CN)
- `30d` → 4 data points, one per week (labels: Tuần 1, Tuần 2, Tuần 3, Tuần 4)

**Response:**
```json
{
  "data": [
    { "name": "T2", "revenue": 500000, "bookings": 1 },
    { "name": "T3", "revenue": 1500000, "bookings": 2 },
    ...
  ]
}
```

#### Recent Activities

```
GET /api/v1/dashboard/recent
```

Returns the 10 most recent bookings, enriched with customer name from `iam-service`.

---

## Data Models

### Entities

#### `RentalGroup` (table: `rental_group`)
The main booking record. One `RentalGroup` can include multiple vehicles.

| Field | Type | Description |
|---|---|---|
| `id` | `Long` | Auto-generated PK |
| `bookingCode` | `String` | Human-readable code, e.g. `BK-20250601-0001` |
| `userId` | `String` | UUID of the customer (from IAM Service) |
| `deliveryMode` | `DeliveryMode` | `SELF_PICKUP` or `DELIVERY` |
| `deliveryAddress` | `String` | Required when `deliveryMode = DELIVERY` |
| `totalAmount` | `BigDecimal` | Sum of all `unitPrice × duration` |
| `depositRequired` | `BigDecimal` | Security deposit amount |
| `status` | `BookingStatus` | Current lifecycle state |
| `rentalUnits` | `List<RentalUnit>` | One per vehicle |
| `invoices` | `List<Invoice>` | Billing records |
| `createdAt` | `LocalDateTime` | Auto-set |

#### `RentalUnit` (table: `rental_unit`)
One vehicle within a booking.

| Field | Type | Description |
|---|---|---|
| `vehicleId` | `Long` | Vehicle ID from Car Management Service |
| `driver` | `DriverProfile` | Assigned driver (nullable for self-drive) |
| `isWithDriver` | `Boolean` | Whether this unit includes a driver |
| `startTime` | `LocalDateTime` | Rental start |
| `endTime` | `LocalDateTime` | Rental end |
| `unitPrice` | `BigDecimal` | Price per day/hour |
| `faultPercent` | `Integer` | Damage percentage assessed on return |
| `status` | `RentalUnitStatus` | `PENDING` → `ACTIVE` → `RETURNED` |

#### `Invoice` (table: `invoice`)

| Field | Type | Description |
|---|---|---|
| `type` | `InvoiceType` | `DEPOSIT`, `RENTAL`, `INCURRED`, or `REFUND` |
| `amount` | `BigDecimal` | Amount due |
| `paymentMethod` | `PaymentMethod` | Set after payment |
| `paidAt` | `LocalDateTime` | `null` = unpaid, non-null = paid |

#### `HandoverProtocol` (table: `handover_protocol`)
Immutable log of a vehicle handover event.

| Field | Type | Description |
|---|---|---|
| `type` | `String` | `PICKUP` or `RETURN` |
| `odoMeter` | `Integer` | Odometer reading at time of handover |
| `condition` | `String` | Free-text condition description |
| `photos` | `String` | JSON array of photo URLs |

#### `DriverProfile` (table: `driver_profile`)

| Field | Type | Description |
|---|---|---|
| `userId` | `String` | UUID from IAM Service (DRIVER role) |
| `licenseNumber` | `String` | Driver's license number |
| `status` | `DriverStatus` | `ACTIVE`, `INACTIVE`, or `BLOCKED` |
| `currentLocation` | `String` | Current GPS or address |
| `averageRating` | `Double` | Computed from feedback |

### Enums

| Enum | Values |
|---|---|
| `BookingStatus` | `PENDING`, `CONFIRMED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `OVERDUE` |
| `RentalUnitStatus` | `PENDING`, `ACTIVE`, `RETURNED`, `CANCELLED` |
| `DeliveryMode` | `SELF_PICKUP`, `DELIVERY` |
| `InvoiceType` | `DEPOSIT`, `RENTAL`, `INCURRED`, `REFUND` |
| `PaymentMethodType` | `CASH`, `BANK_TRANSFER`, `CREDIT_CARD`, `E_WALLET` |
| `DriverStatus` | `ACTIVE`, `INACTIVE`, `BLOCKED` |

---

## Inter-Service Communication

This service calls two external services using **OpenFeign** (direct HTTP, bypassing the API Gateway):

### → IAM Service (`:8080`)

| Feign Method | Endpoint | Purpose |
|---|---|---|
| `getUserById(userId)` | `GET /internal/users/{userId}` | Resolve user name/email/phone for booking responses |
| `userExists(userId)` | `GET /internal/users/{userId}/exists` | Validate customer ID on booking creation |
| `getUsersByRole(roleName)` | `GET /internal/users/role/{roleName}` | List all drivers for staff assignment UI |

### → Car Management (`:8081`)

| Feign Method | Endpoint | Purpose |
|---|---|---|
| `getVehicleById(id)` | `GET /api/v1/vehicles/{id}` | Validate vehicle exists and is `AVAILABLE` before booking |
| `getVehiclesByStatus(status)` | `GET /api/v1/vehicles/status/{status}` | Proxy endpoint for frontend |

### Graceful Degradation

All Feign calls are wrapped in `try/catch`. If `iam-service` or `car-management` is unavailable:
- Booking creation falls back to **skipping vehicle status validation** (logs a warning)
- Response enrichment (customer name, vehicle details) returns `null` fields — the booking still succeeds

---

## Environment Variables

| Variable | Description | Default (dev) |
|---|---|---|
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/booking_service_db` |
| `SPRING_DATASOURCE_USERNAME` | DB username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password | `postgres` |
| `SERVER_PORT` | HTTP port | `8082` |
| `FEIGN_IAM_URL` | IAM service base URL | `http://localhost:8080` |
| `FEIGN_CAR_MANAGEMENT_URL` | Car management base URL | `http://localhost:8081` |
