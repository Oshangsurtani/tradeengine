// Generates a list of limit and market orders for testing purposes.
// Usage: node gen_orders.js <count> > orders.json

const crypto = require('crypto');

function randomOrder(i) {
  const side = Math.random() < 0.5 ? 'buy' : 'sell';
  const type = Math.random() < 0.8 ? 'limit' : 'market';
  const price = 30000 + Math.random() * 40000;
  const quantity = 0.01 + Math.random() * 2;
  return {
    clientId: 'client-' + (i % 100),
    instrument: 'BTC-USD',
    side,
    type,
    price: Math.round(price * 100) / 100,
    quantity: Math.round(quantity * 1000) / 1000
  };
}

const count = parseInt(process.argv[2] || '1000');
for (let i = 0; i < count; i++) {
  console.log(JSON.stringify(randomOrder(i)));
}