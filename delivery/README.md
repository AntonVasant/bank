# Delivery — Notification Service

## Overview

**Delivery** is a notification service application owned and operated by the **Central Bank**. Its primary responsibility is to receive and deliver data notifications to other banks and financial institutions within the banking network.

The service acts as a central distribution point, consuming messages and propagating relevant financial data to downstream bank consumers in a reliable and consistent manner.

---

## Technology Stack

| Component        | Technology                          |
|-----------------|--------------------------------------|
| Language         | Java 17                             |
| Framework        | Spring Boot 4.1.0                   |
| Web Layer        | Spring MVC                          |
| Database         | PostgreSQL 17                       |
| Messaging        | Apache Kafka                        |
| Build Tool       | Maven                               |
| Containerization | Docker / Docker Compose             |

---

## Architecture

The service listens to Kafka topics and consumes messages produced by the Central Bank's core systems. Upon receiving a message, it processes and delivers the notification data to the appropriate recipient banks.

```
Central Bank Core Systems
        │
        ▼
  [ Kafka Broker ]
        │
        ▼
 [ Delivery Service ]  ──▶  PostgreSQL (deliverydb)
        │
        ▼
  Recipient Banks
```

---

## Configuration

### Application Properties

| Property              | Default Value        | Description                          |
|-----------------------|----------------------|--------------------------------------|
| `server.port`         | `8084`               | HTTP port the service listens on     |
| `POSTGRES_HOST`       | `localhost`          | PostgreSQL host                      |
| `POSTGRES_PORT`       | `5436`               | PostgreSQL port                      |
| `POSTGRES_USER`       | `postgres`           | PostgreSQL username                  |
| `POSTGRES_PASSWORD`   | `postgres`           | PostgreSQL password                  |
| Kafka bootstrap       | `localhost:9092`     | Kafka broker address                 |
| Kafka consumer group  | `delivery-group`     | Consumer group ID                    |

### Logging

Logs are written to `logs/delivery.log` with daily rolling policy, a maximum file size of 10 MB, and a 30-day history retention.

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.x
- Docker & Docker Compose

### Run Infrastructure

Start the PostgreSQL database using Docker Compose:

```bash
docker-compose up -d
```

### Build & Run the Application

```bash
./mvnw clean install
./mvnw spring-boot:run
```

The service will start on port **8084**.

---

## Dependencies

- `spring-boot-starter-webmvc` — REST web layer
- `postgresql` — JDBC driver for PostgreSQL
- `lombok` — Boilerplate reduction
- `payment-contract` — Shared contract library from the payments domain

---

## Ownership

This service is owned and maintained by the **Central Bank** infrastructure team. It is a critical component of the inter-bank notification pipeline and must remain highly available.
