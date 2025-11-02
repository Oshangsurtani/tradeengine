# Design Document – Spring Boot Trade Engine

## Overview

This document describes the architecture and design decisions for the Spring Boot implementation of a real‑time trade clearing and analytics engine.  The system ingests orders via HTTP, matches them according to price–time priority, persists state in PostgreSQL, caches idempotency results in Redis, exposes read APIs, streams real‑time updates, and publishes metrics for observability.

## Architecture

### Components

* **API Layer** – Spring MVC controllers define REST endpoints for submitting orders, cancelling orders, querying the order book and trades, and subscribing to real‑time updates via Server‑Sent Events (SSE).  Request/response payloads are automatically serialized/deserialized.
* **Matching Engine** – A singleton service processes order events sequentially.  Incoming orders and cancellations are enqueued and processed by a dedicated worker thread.  This design avoids race conditions by ensuring only one thread mutates the order book at a time.  The engine interacts with:
  * **OrderBook** – An in‑memory representation of open limit orders, partitioned into bids and asks sorted by price–time.  When a limit order cannot be fully matched, it is inserted into the book.  Market orders consume resting liquidity until the book is exhausted.
  * **Repositories** – Spring Data JPA repositories persist orders and trades to PostgreSQL.  Orders are versioned via optimistic locking to prevent concurrent updates.  Trades record executions with references to the buy and sell orders.
  * **Redis** – Idempotency keys map to completed orders so that repeated submissions with the same key return the same result.  Redis is used as a fast key/value store.
  * **Metrics** – Micrometer counters, gauges and timers record the number of received, matched and rejected orders, the depth of the order book and the latency distribution of order processing.  These metrics are exported via the Prometheus registry and visualised in Grafana.
  * **StreamService** – Manages SSE clients and broadcasts events to subscribers whenever trades occur or orders change state.
  * **EventService** – Persists every state transition as an append‑only event (`ORDER_CREATED`, `ORDER_UPDATED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`) for auditability and replay.  Events store a timestamp, aggregate ID and a JSON payload of the affected order or trade.
  * **SnapshotService** – Periodically captures the in‑memory order book per instrument and writes it to a snapshot table.  Snapshots allow the system to restore to a recent state without replaying the entire event history.
  * **BinanceWebSocketClient** – Optionally connects to Binance’s aggregate trade stream and converts each trade into a market order.  This demonstrates ingestion from an external WebSocket feed and can be extended to other streaming sources (Kafka/NATS).
* **Persistence & Recovery** – Orders, trades, snapshots and events are persisted to PostgreSQL via Spring Data JPA.  Idempotency keys are stored in Redis.  On startup, the engine performs a two‑phase recovery for each instrument: (1) restore the latest snapshot (if present) into the in‑memory order book, and (2) replay only those events recorded after the snapshot timestamp to bring the state up to date.  If no snapshot exists, open or partially filled orders are loaded directly from the orders table.  This strategy provides fast restart times while maintaining a complete audit trail via the event log.
* **Messaging** – The primary mechanism for client updates is Server‑Sent Events (SSE).  Clients subscribe to `/stream` to receive order and trade events in real time.  WebSockets are used for ingestion of external market data.  The architecture can be extended to use message brokers like Kafka or NATS for both ingestion and distribution.

## Concurrency Model

HTTP requests are handled by Spring’s default thread pool.  To avoid race conditions when modifying the order book and persisting changes, the matching engine partitions events by instrument.  Each instrument maintains its own `OrderBook`, `BlockingQueue` and dedicated worker thread (`InstrumentEngine`).  Submissions and cancellations for `BTC‑USD` are processed in a separate queue from those for `ETH‑USD`, enabling the system to match multiple markets concurrently without locking.  Within an instrument, events are processed sequentially to guarantee deterministic ordering and eliminate race conditions.  Database writes and event persistence occur within the worker thread, ensuring that persisted state and in‑memory state remain consistent.

Optimistic locking on the `Order` entity (`@Version` field) protects against concurrent updates that bypass the engine (for example, direct database modifications or administrative corrections) but should not be triggered during normal operation.

## Trade Matching Logic

Orders contain a side (`buy` or `sell`), type (`limit` or `market`), price and quantity.  For limit orders, the engine matches against the best available opposite orders whose prices satisfy the limit constraint.  Market orders match until the book is exhausted.  Partial fills update the `filledQuantity` of both orders and generate a `Trade` record with quantity, price and timestamp.  When an order’s `filledQuantity` equals its `quantity`, its status is set to `filled` and the order is removed from the order book.  If a limit order remains partially unfilled, it is inserted back into the book.  The engine persists each state transition and trade.

## Load Testing

Two load test scripts are provided:

* `load-test/loadTest.js` – A Node.js script that spawns a configurable number of concurrent HTTP clients, submits orders and reports p50/p90/p99 latencies.
* `load-test/k6_load_test.js` – A [k6](https://k6.io) script that uses the `constant-arrival-rate` executor to generate 2,000 orders per second.  The script assigns unique idempotency keys per iteration and checks response status.  k6 outputs latency percentiles and can integrate with Prometheus via its remote write adapter.

Running these scripts against the Dockerized deployment provides empirical performance numbers.  Use them to tune JVM settings, database connection pools and worker thread counts to achieve the 2k/sec target with sub‑100ms latency.

## Scaling & Future Work

To scale beyond a single node and instrument, the system could be partitioned by instrument, with a dedicated matching engine and order book per partition.  A message broker (Kafka/NATS) would distribute orders to the appropriate engine.  Idempotency keys could be namespaced by instrument to avoid cross‑partition collisions.  Persisting only events rather than state (event sourcing) would simplify recovery and enable replaying streams for analytics.  Additional features such as authentication, rate limiting, VWAP calculations and settlement services are described in the assignment’s bonus section.