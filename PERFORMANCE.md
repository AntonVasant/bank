# Performance Investigation — Interbank Payment Throughput

This document records the results of load-testing the interbank payment path
(`transaction` ⇄ `rail`) with the k6 suite in
[`transaction/load-test/k6`](transaction/load-test/k6), the environment it was
run in, and the bottleneck analysis that came out of it.

## Test environment

All services and their infra were run **on a single laptop**, sharing the
same CPU/memory/disk:

| Spec | Value |
|---|---|
| Machine | MacBook (Apple M3 Pro) |
| CPU cores | 11 |
| RAM | 18 GB |
| OS | macOS 26.5.2 |

### Processes running during the tests

**Application JVMs** (each a separate `java` process):

| Service | Port(s) | Notes |
|---|---|---|
| `transaction` (Bank A, profile `bankA`) | 8080 (REST), 9095 (gRPC) | `bank.code=AXIS_IN` |
| `transaction` (Bank B, profile `bankB`) | 8081 (REST), 9091 (gRPC) | `bank.code=HDFC_IN` |
| `rail` | 8082 (REST), 9090 (gRPC) | settlement/cycle broadcast |
| `delivery` | 8084 | **not running** during the throughput tests (out of scope — settlement/reconciliation delivery wasn't under test) |

**Infra** (Docker containers + a couple of native processes):

| Container/process | Purpose |
|---|---|
| `postgres-banka` (Postgres 17) | Bank A's database |
| `postgres-bankb` (Postgres 17) | Bank B's database |
| `postgres-rail` (Postgres 17) | rail's database |
| `redis` | Bank A/B idempotency + cycle cache |
| `redis-rail` | rail's cache |
| `tigerbeetle-rail` | ledger engine used by rail |
| `kafka-ui-rail` | Kafka UI (not on the critical path) |
| native `kafka.Kafka` + `zookeeper` processes | event bus for settlement/outbox events |

Plus IntelliJ IDEA (running the JVMs) and the k6 client itself, all
competing for the same 11 cores.

### Load test tool

[k6](https://k6.io) v2.1.0, using an **open-model** load profile
(`constant-arrival-rate` executor — requests are fired on a fixed schedule
regardless of response latency, with no artificial `sleep()` between
requests). Traffic was spread across many independent account pairs
(`ACCOUNT_PAIRS`, up to 1000) rather than one shared account, specifically to
rule out Postgres row-level lock contention as an artificial bottleneck.

## What was tested

Interbank payments only (Bank A ↔ Bank B via `rail`) — the path that
exercises the REST API, `paymentExecutor` thread pool, HikariCP, Postgres,
Redis, and the async gRPC hand-off to `rail`. Only "Moment 1" (the hold being
placed and the HTTP response returned) was measured; the settlement/delivery
completion was out of scope for this round of testing.

## Results summary

| Target TPS (total, split evenly both directions) | Achieved TPS | Outcome |
|---|---|---|
| 100 | 100.2 | ✅ clean, 0 failures |
| 200 | 200.2 | ✅ clean, 0 failures |
| **500** | **500.2** | ✅ **clean, 0 failures — best confirmed sustainable ceiling** |
| 600 – 700 | 510 – 610 | ⚠️ starts falling behind (requests queue, no hard failures yet) |
| 800 | 435 | ❌ hard failures begin (`HTTP 0` connection-level errors) |
| 1000 | 260 – 330 | ❌ collapsing further |
| 2000 (default) | 60 – 110 | ❌ near-total collapse; ~75% of attempted requests dropped |

**Bottom line: the stack reliably sustains roughly 400-550 TPS** on this
machine. 2000 TPS (the original target) is **~4x beyond** what it can
currently handle. Above the ~500 TPS mark, throughput doesn't plateau — it
actively *degrades* as target load increases, which is the signature of a
queueing collapse rather than a hard capacity ceiling.

## Bottleneck investigation

### Ruled out: Postgres row-level lock contention
Traffic was spread across up to 1000 independent account pairs (instead of
one shared account) — results were statistically identical to the
single-account case, ruling out lock contention on individual account rows as
the cause.

### Ruled out: Postgres/database CPU capacity
`docker stats` sampled *during* live test runs showed all three Postgres
containers essentially idle (<1% CPU) even while the app was failing
requests — the database itself was never the resource under pressure.

### Confirmed: HikariCP connection pool exhaustion → cascading queue collapse
A thread dump of Bank B during a collapsed run showed **all 200
`payment-` executor threads simultaneously blocked** in
`HikariPool.getConnection()`, waiting to borrow a JDBC connection: