# Trade Engine – Spring Boot Edition

This project implements a simplified real‑time trade matching and analytics engine in Java using Spring Boot.  It satisfies the requirements of the backend engineering assignment: ingestion of orders over HTTP, matching via price‑time priority, persistence in PostgreSQL, idempotent submissions with Redis, public APIs for market data, Server‑Sent Events streaming, Prometheus‑compatible metrics, and Dockerized deployment.

## Features

* **Order ingestion** – `POST /orders` accepts limit and market orders with idempotency support via Redis.
* **Matching engine** – A single worker thread processes events from a queue, ensuring atomic matching and eliminating race conditions.  The in‑memory order book matches market orders immediately and inserts unfilled limit orders into the book.
* **Persistence** – Orders and trades are persisted via Spring Data JPA into a PostgreSQL database.  The engine uses optimistic locking to avoid concurrent updates.
* **Idempotency** – Redis stores the result of each submitted order keyed by the idempotency key to guarantee exactly‑once semantics.
* **API key security & rate limiting** – All endpoints (except actuator) require an `X-API-Key` header matching the configured key (`app.api-key`).  A simple in‑memory rate limiter caps order submissions to 100 requests per second to mitigate DoS attacks.
* **Public APIs** – Endpoints to view the current order book (`/orderbook`), recent trades (`/trades`), and individual orders (`/orders/{id}`).
* **Analytics API** – `/analytics/vwap` computes the volume‑weighted average price over the last `n` minutes.
* **Streaming updates** – Clients may subscribe to `/stream` to receive real‑time events for trades and order state changes using Server‑Sent Events (SSE).
* **Observability** – Spring Boot Actuator exposes health checks and Prometheus metrics.  Custom counters and timers measure orders received, matched and rejected, as well as latency distributions.
* **Dockerized** – A Dockerfile builds the application and a `docker-compose.yml` launches Postgres, Redis and the application together.

## Structure

```
trade-engine-java-spring/
├── pom.xml                    # Maven build definition
├── src/main/java/com/example/tradeengine/
│   ├── TradeEngineApplication.java    # Spring Boot entry point
│   ├── model/
│   │   ├── Order.java         # JPA entity for orders
│   │   └── Trade.java         # JPA entity for trades
│   ├── repository/
│   │   ├── OrderRepository.java  # Spring Data repository
│   │   └── TradeRepository.java
│   ├── engine/
│   │   ├── OrderBook.java      # In‑memory order book
│   │   └── MatchingEngine.java # Matching logic with event queue
│   ├── service/
│   │   └── StreamService.java  # SSE client manager
│   ├── controller/
│   │   ├── OrderController.java
│   │   ├── OrderBookController.java
│   │   ├── TradeController.java
│   │   └── StreamController.java
│   └── ...
├── src/main/resources/application.yml # Spring configuration
├── Dockerfile
├── docker-compose.yml
├── fixtures/
│   └── gen_orders.js            # Script to generate test orders
├── load-test/
│   └── loadTest.js              # Simple load test script
└── README.md
```

## Building and running

Assuming you have Maven and Docker installed, run the following commands:

```bash
cd trade-engine-java-spring

# Build the application JAR
mvn clean package -DskipTests

# Start Postgres, Redis and the application
docker-compose up --build

# The service will be available at http://localhost:8080
```

### API examples

Create a limit order:

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: abc-123" \
  -H "X-API-Key: secret-key" \
  -d '{"clientId":"client1","instrument":"BTC-USD","side":"buy","type":"limit","price":70000,"quantity":0.5}'
```

Cancel an order:

```bash
curl -X POST http://localhost:8080/orders/<orderId>/cancel
```

View the order book:

```bash
curl http://localhost:8080/orderbook?levels=10
```

Listen for streaming events:

```bash
curl -N http://localhost:8080/stream
```

VWAP analytics:

```bash
curl http://localhost:8080/analytics/vwap?minutes=5 -H "X-API-Key: secret-key"
```

Metrics and health endpoints are exposed at `/actuator/metrics` and `/actuator/health` respectively.

## Fixtures and load testing
The `fixtures/gen_orders.js` script generates a large set of random limit and market orders for testing.  Two load testing scripts are provided:

* `load-test/loadTest.js` – a simple Node.js script that fires a configurable number of concurrent requests and calculates p50/p90/p99 latencies.
* `load-test/k6_load_test.js` – a [k6](https://k6.io) scenario that drives a constant arrival rate of 2,000 orders per second using the `constant-arrival-rate` executor.  To run the k6 test, install k6 and execute:

  ```bash
  k6 run load-test/k6_load_test.js
  ```

The k6 script will output latency and throughput metrics which can be compared against the 2k/sec performance target.

## Monitoring and alerting

The service integrates with **Prometheus** and **Grafana** for observability.  Running `docker-compose up` will start Prometheus scraping the `/actuator/prometheus` endpoint and Grafana with a pre‑provisioned dashboard.  The dashboard (defined in `grafana/dashboards/trade_engine_dashboard.json`) displays the rate of orders received/matched and p95 latency using the `histogram_quantile` function.  Logs are structured as JSON via Logback and are suitable for ingestion into an ELK or Loki stack.  Actuator readiness and liveness probes are enabled (see `application.yml`) and can be used by Kubernetes health checks; a sample deployment manifest is provided in `k8s/deployment.yaml`.

## Comprehensive testing

Automated tests live under `src/test/java`.  Unit tests verify order‑book sorting and cancellation (`OrderBookTest`), while integration tests exercise the REST API via `MockMvc` (`OrderControllerTest`) and the matching engine (`MatchingEngineTest`).  The matching tests submit limit and market orders end‑to‑end, assert that trades are created, and verify idempotent submissions.  To run the tests:

```bash
mvn test
```

## Design and recovery

See `DESIGN.md` for a detailed design document covering architecture, concurrency model, persistence, recovery strategy, event sourcing and snapshotting, as well as scaling considerations.