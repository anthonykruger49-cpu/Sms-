// Minimal SMS relay: your apps POST /api/send, the phone polls /api/device/pending.
// In-memory queue - swap for Redis/SQLite if you need persistence.
const express = require('express');
const crypto = require('crypto');
const app = express();
app.use(express.json());

const API_KEY = process.env.API_KEY || 'change-me';
const queue = new Map(); // id -> {id,to,body,status,error,created}

app.use('/api', (req, res, next) => {
  if (req.get('X-API-Key') !== API_KEY) return res.status(401).json({ error: 'unauthorized' });
  next();
});

// Send an SMS (called by your website / scripts)
app.post('/api/send', (req, res) => {
  const { to, body } = req.body || {};
  if (!to || !body) return res.status(400).json({ error: 'to and body required' });
  const id = crypto.randomUUID();
  queue.set(id, { id, to, body, status: 'queued', error: '', created: Date.now() });
  res.json({ id, status: 'queued' });
});

app.get('/api/status/:id', (req, res) => {
  const m = queue.get(req.params.id);
  m ? res.json(m) : res.status(404).json({ error: 'not found' });
});

// Phone polls here
app.get('/api/device/pending', (req, res) => {
  const limit = Math.min(parseInt(req.query.limit) || 10, 50);
  const out = [];
  for (const m of queue.values()) {
    if (m.status === 'queued' && out.length < limit) { m.status = 'sending'; out.push({ id: m.id, to: m.to, body: m.body }); }
  }
  res.json({ messages: out });
});

app.post('/api/device/report', (req, res) => {
  const m = queue.get(req.body.id);
  if (m) { m.status = req.body.status; m.error = req.body.error || ''; }
  res.json({ ok: true });
});

app.listen(process.env.PORT || 3000, () => console.log('Net2SMS server running'));
