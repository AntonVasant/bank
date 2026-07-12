# API Contract

## Base URL

```
/api/v1
```

---

## Account APIs

### Create Account

**Request**

```
POST /api/v1/accounts
```

```json
{
  "userId": 1,
  "accountNumber": "ACC100001"
}
```

**Response** `201 Created`

```json
{
  "accountId": 1,
  "accountNumber": "ACC100001",
  "status": "ACTIVE"
}
```

---

### Get Account

**Request**

```
GET /api/v1/accounts/{accountId}
```

**Response** `200 OK`

```json
{
  "accountId": 1,
  "accountNumber": "ACC100001",
  "status": "ACTIVE",
  "userId": 1
}
```

---

## Single Payment APIs

### Create Payment

**Headers**

```
Idempotency-Key: txn-001
```

**Request**

```
POST /api/v1/payments
```

```json
{
  "paymentReference": "PAY-001",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 1000
}
```

**Response** `201 Created`

```json
{
  "paymentId": 1,
  "paymentReference": "PAY-001",
  "status": "PROCESSING"
}
```

---

### Get Payment Status

**Request**

```
GET /api/v1/payments/{paymentId}
```

**Response** `200 OK`

```json
{
  "paymentId": 1,
  "paymentReference": "PAY-001",
  "sourceAccountId": 1,
  "destinationAccountId": 2,
  "amount": 1000,
  "status": "SUCCESS"
}
```

---

## Bulk Payment APIs

### Create Bulk Payment Batch

**Headers**

```
Idempotency-Key: batch-june-salary-001
```

**Request**

```
POST /api/v1/payments/bulk
```

```json
{
  "batchReference": "SALARY-JUNE-2026",
  "description": "June Salary Processing",
  "payments": [
    {
      "paymentReference": "PAY-101",
      "sourceAccountId": 1,
      "destinationAccountId": 2,
      "amount": 50000
    },
    {
      "paymentReference": "PAY-102",
      "sourceAccountId": 1,
      "destinationAccountId": 3,
      "amount": 60000
    }
  ]
}
```

**Response** `201 Created`

```json
{
  "batchId": 1,
  "batchReference": "SALARY-JUNE-2026",
  "status": "PROCESSING",
  "totalPayments": 2
}
```

---

### Get Batch Status

**Request**

```
GET /api/v1/batches/{batchId}
```

**Response** `200 OK`

```json
{
  "batchId": 1,
  "batchReference": "SALARY-JUNE-2026",
  "status": "COMPLETED",
  "totalPayments": 2,
  "successfulPayments": 2,
  "failedPayments": 0
}
```

---

### Get Batch Payments

**Request**

```
GET /api/v1/batches/{batchId}/payments
```

**Response** `200 OK`

```json
{
  "batchId": 1,
  "payments": [
    {
      "paymentId": 1,
      "paymentReference": "PAY-101",
      "status": "SUCCESS"
    },
    {
      "paymentId": 2,
      "paymentReference": "PAY-102",
      "status": "SUCCESS"
    }
  ]
}
```

---

## Status Values

### Payment Status

| Value | Description |
|---|---|
| `PENDING` | Payment created, awaiting processing |
| `PROCESSING` | Payment is being processed |
| `SUCCESS` | Payment completed successfully |
| `FAILED` | Payment processing failed |

### Batch Status

| Value | Description |
|---|---|
| `PENDING` | Batch created, awaiting processing |
| `PROCESSING` | Batch is being processed |
| `COMPLETED` | All payments in the batch succeeded |
| `PARTIALLY_COMPLETED` | Some payments failed |
| `FAILED` | All payments failed |

### Outbox Status

| Value | Description |
|---|---|
| `PENDING` | Event pending publication to Kafka |
| `PROCESSED` | Event successfully published |
| `FAILED` | Event publication failed |

### Account Status

| Value | Description |
|---|---|
| `ACTIVE` | Account is active and operational |
| `INACTIVE` | Account is inactive |
| `BLOCKED` | Account is blocked from transactions |

---

## Idempotency Rules

### Single Payment

Idempotency key must be unique per payment request.

```
Idempotency-Key: txn-001
```

- If the same key is submitted again with the **same payload**, the previously stored response is returned.
- If the same key is submitted with a **different payload**, the API returns:

```
409 Conflict
```

### Bulk Payment

The idempotency key represents the entire batch request.

```
Idempotency-Key: batch-june-salary-001
```

- The same batch must never be processed twice.
- Resubmitting the same key with the same payload returns the original batch response.
- Resubmitting the same key with a different payload returns `409 Conflict`.
