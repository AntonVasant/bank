# Architecture Overview

This repository is composed of three independent applications that together simulate a simplified banking and interbank settlement ecosystem.

## 1. `transaction`

The `transaction` application acts as a **commercial bank** (e.g. JP Morgan, American Express, or any similar retail/commercial bank).

- Owns customer accounts and balances.
- Initiates and processes transactions (payments, transfers, etc.) on behalf of its customers.
- Communicates with the `rail` application to settle interbank transactions.

## 2. `rail`

The `rail` application represents the **central bank** (e.g. the Federal Reserve, Reserve Bank of India, or a similar central banking authority).

- Acts as the settlement layer between banks.
- Runs settlement cycles/jobs that process and finalize interbank payments.
- Exposes gRPC/proto-based APIs (see `payment.proto`) that `transaction` applications use to settle payments.

## 3. `delivery`

The `delivery` application is the **notification service**.

- Responsible for delivering notifications (e.g. payment status updates, alerts) to the relevant parties.
- Decouples notification concerns from the core transaction and settlement logic.

## How They Fit Together

```mermaid
flowchart LR
    T[transaction<br/>(Commercial Bank)] -->|Settle payment| R[rail<br/>(Central Bank)]
    R -->|Settlement result| T
    T -->|Notify| D[delivery<br/>(Notification Service)]
    R -->|Notify| D
```

- **`transaction`** initiates payments as a commercial bank.
- **`rail`** settles those payments centrally, like a central bank clearing interbank transactions.
- **`delivery`** handles sending out notifications related to transactions and settlements to end users or other systems.
