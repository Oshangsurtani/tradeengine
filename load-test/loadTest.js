// Simple load test script for the trade engine.
// Submits concurrent orders to the /orders endpoint and reports latency.

const http = require('http');

function sendOrder(host, port, order, idempotencyKey) {
  return new Promise((resolve, reject) => {
    const data = JSON.stringify(order);
    const options = {
      hostname: host,
      port: port,
      path: '/orders',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data),
        'Idempotency-Key': idempotencyKey
      }
    };
    const req = http.request(options, res => {
      res.on('data', () => {});
      res.on('end', () => resolve());
    });
    req.on('error', err => reject(err));
    req.write(data);
    req.end();
  });
}

async function runLoad(host, port, totalOrders, concurrency) {
  const orders = [];
  // generate orders similar to fixture
  for (let i = 0; i < totalOrders; i++) {
    const side = Math.random() < 0.5 ? 'buy' : 'sell';
    const type = Math.random() < 0.8 ? 'limit' : 'market';
    const price = 30000 + Math.random() * 40000;
    const quantity = 0.01 + Math.random() * 2;
    orders.push({
      clientId: 'load-client-' + (i % 100),
      instrument: 'BTC-USD',
      side,
      type,
      price: Math.round(price * 100) / 100,
      quantity: Math.round(quantity * 1000) / 1000
    });
  }
  let latencies = [];
  let inFlight = 0;
  let idx = 0;
  return new Promise(resolve => {
    function next() {
      if (idx >= orders.length && inFlight === 0) {
        // done
        latencies.sort((a, b) => a - b);
        const n = latencies.length;
        const p50 = latencies[Math.floor(0.5 * n)];
        const p90 = latencies[Math.floor(0.9 * n)];
        const p99 = latencies[Math.floor(0.99 * n)];
        console.log('Completed', n, 'orders');
        console.log('p50 latency ms:', p50.toFixed(2));
        console.log('p90 latency ms:', p90.toFixed(2));
        console.log('p99 latency ms:', p99.toFixed(2));
        resolve();
        return;
      }
      while (inFlight < concurrency && idx < orders.length) {
        const order = orders[idx++];
        const idem = 'load-' + idx;
        const start = Date.now();
        inFlight++;
        sendOrder(host, port, order, idem).then(() => {
          const end = Date.now();
          latencies.push(end - start);
          inFlight--;
          next();
        }).catch(() => {
          inFlight--;
          next();
        });
      }
    }
    next();
  });
}

const host = process.argv[2] || 'localhost';
const port = parseInt(process.argv[3] || '8080');
const total = parseInt(process.argv[4] || '1000');
const concurrency = parseInt(process.argv[5] || '20');
runLoad(host, port, total, concurrency).then(() => process.exit());