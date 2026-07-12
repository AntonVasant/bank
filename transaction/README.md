# Transaction Processing System

## Overview

A production-inspired transaction processing system built using Spring Boot, PostgreSQL, Redis, Kafka, and TigerBeetle.

The system focuses on reliability, consistency, and scalability by implementing patterns commonly used in financial systems such as:

* Idempotent transaction processing
* Double-entry ledger accounting (TigerBeetle)
* Outbox Pattern
* Event-Driven Architecture
* Bulk Payment Processing
* Transaction Status Tracking
* Retry-safe APIs

---

## Architecture

![Architecture Diagram](docs/architecture/images/architecture.png)

The architecture diagram can be found in:

docs/images/architecture.png

---

## Tech Stack

### Backend

* Java
* Spring Boot
* Spring Data JPA
* Spring Validation

### Data Layer

* PostgreSQL
* Redis
* TigerBeetle

### Messaging

* Apache Kafka

### Infrastructure

* Docker
* Docker Compose

---

## Core Features

### Account Management

* Create accounts
* Manage account status
* Link accounts to users

### Payment Processing

* Single payment API
* Bulk payment API
* Payment status tracking

### Idempotency

* Prevent duplicate transaction execution
* Retry-safe APIs
* Request hash validation

### Event Publishing

* Outbox Pattern
* Kafka integration
* Reliable event delivery

### Ledger Management

* Double-entry accounting
* Atomic transfers
* Balance tracking using TigerBeetle

---

## Domain Model

### User

Represents a customer within the system.

### Account

Represents a payment account owned by a user.

### Payment

Represents a single money transfer between two accounts. Can be standalone or part of a `PaymentBatch`.

### PaymentBatch

Represents a bulk payment operation grouping multiple individual payments. Tracks aggregate counts of total, successful, and failed payments.

### IdempotencyRecord

Stores idempotency information and cached responses.

### OutboxEvent

Stores events that will later be published to Kafka.

---

## Project Structure

transaction-api/

├── docs/

│ ├── architecture.md

│ ├── database-schema.md

│ └── images/

│ └── architecture.png

├── src/

├── docker-compose.yml

├── pom.xml

└── README.md

---

## Development Roadmap

### Phase 1

* User Management
* Account Management
* Payment Entity

### Phase 2

* Idempotency Implementation
* Redis Integration

### Phase 3

* Kafka Integration
* Outbox Pattern

### Phase 4

* TigerBeetle Integration

### Phase 5

* Bulk Payments
* Monitoring
* Metrics

---

## AI Context

When generating code, use the following assumptions:

* PostgreSQL stores application metadata.
* Redis stores idempotency and cache data.
* TigerBeetle is the source of truth for balances.
* Kafka publishes domain events.
* Spring Boot acts as the orchestration layer.
* All payment APIs must be idempotent.
* Payments should never be processed twice.
