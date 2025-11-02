import http from 'k6/http';
import { check } from 'k6';

/*
 * k6 load test for the trade engine.  This script issues a constant
 * arrival rate of order submissions against the /orders endpoint.
 * The rate and duration can be adjusted via the options below.  Each
 * iteration generates a random limit order for the BTC‑USD instrument
 * and supplies a unique idempotency key based on the virtual user and
 * iteration number.  Response status is asserted to be 200.
 *
 * Example execution (requires k6 installed):
 *   k6 run --vus=1000 --duration=10s k6_load_test.js
 *
 * To achieve ~2k orders/sec sustained, use the constant‑arrival‑rate
 * executor below.  The preAllocatedVUs should be sized high enough
 * to cope with the arrival rate.  Results will include latency
 * percentiles which can be compared against the performance targets.
 */

export const options = {
  scenarios: {
    orders_rate: {
      executor: 'constant-arrival-rate',
      rate: 2000, // orders per second
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 500,
      maxVUs: 2000,
    },
  },
};

export default function () {
  const clientId = `k6-${__VU}`;
  const side = Math.random() < 0.5 ? 'buy' : 'sell';
  const type = Math.random() < 0.8 ? 'limit' : 'market';
  const price = 30000 + Math.random() * 10000;
  const quantity = 0.01 + Math.random() * 2;
  const order = {
    clientId: clientId,
    instrument: 'BTC-USD',
    side: side,
    type: type,
    price: Number(price.toFixed(2)),
    quantity: Number(quantity.toFixed(3)),
  };
  const idem = `${__VU}-${__ITER__}`;
  const res = http.post('http://localhost:8080/orders', JSON.stringify(order), {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idem,
    },
  });
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}