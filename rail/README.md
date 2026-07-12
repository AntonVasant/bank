# Rail — Central Bank Payment Rail

## Overview

**Rail** is a central bank settlement system that serves as the backbone of interbank money movement. Commercial banks hold **reserve accounts** within this central bank, and all fund transfers between participating banks are settled through these reserves.

## Responsibilities

### Reserve Account Management
- Maintains reserve accounts on behalf of each participating commercial bank
- Tracks real-time reserve balances and enforces liquidity constraints
- Ensures that no bank's reserve account is overdrawn during settlement

### Payment Processing
- Acts as the sole payment processor for all interbank transactions
- Validates, authorizes, and executes transfer instructions submitted by member banks
- Guarantees atomicity of settlements — funds are debited from the sender's reserve and credited to the receiver's reserve in a single transaction

### Interbank Communication
- Serves as the trusted intermediary when a transfer originates at one bank and is destined for an account at another bank
- Coordinates the messaging and acknowledgment flow between the originating bank and the beneficiary bank
- Notifies both parties of settlement outcomes (success, failure, or pending)

## How an Interbank Transfer Works

```
Originating Bank                  Rail (Central Bank)               Beneficiary Bank
       |                                  |                                  |
       |---  Transfer Request  ---------> |                                  |
       |                                  |-- Validate sender reserve        |
       |                                  |-- Debit sender reserve           |
       |                                  |-- Credit receiver reserve        |
       |                                  |-- Notify beneficiary ----------> |
       |                                  |                                  |-- Apply credit to
       |                                  |                                  |   customer account
       | <-- Settlement Confirmation ---- |  <-- Acknowledgment ------------ |
```

1. The **originating bank** submits a transfer instruction to Rail via gRPC.
2. Rail validates the instruction and checks that the originating bank holds sufficient reserves.
3. Rail atomically debits the originating bank's reserve account and credits the beneficiary bank's reserve account.
4. Rail notifies the **beneficiary bank** of the incoming funds so it can apply the credit to the end customer's account.
5. Both banks receive a final settlement confirmation.

## Key Concepts

| Concept | Description |
|---|---|
| **Reserve Account** | An account held by a commercial bank at the central bank used to settle obligations |
| **Settlement** | The final, irrevocable transfer of funds between reserve accounts |
| **Payment Rail** | The infrastructure and rules governing how payments flow between member banks |
| **Outbox** | Reliable event-driven mechanism ensuring settlement events are delivered even during partial failures |
