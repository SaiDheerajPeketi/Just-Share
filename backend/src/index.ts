import dotenv from 'dotenv';
dotenv.config();

import express from 'express';
import quotaRoutes from './routes/quota';
import purchaseRoutes from './routes/purchases';
import telemetryRoutes from './routes/telemetry';
import relayRoutes from './routes/relay';
import { PubSubService } from './services/PubSubService';
import { pubSubAuth } from './middleware/pubSubAuth';

const app = express();
const port = process.env.PORT || 8080;

app.disable('x-powered-by');
app.use(express.json({ limit: '16kb' }));
app.get('/healthz', (_req, res) => res.status(200).json({ status: 'ok' }));

app.use('/quota', quotaRoutes);
app.use('/purchase', purchaseRoutes);
app.use('/telemetry', telemetryRoutes);
app.use('/relay', relayRoutes);

const pubSubService = new PubSubService();

app.post('/pubsub/rtdn', pubSubAuth, async (req, res) => {
  try {
    if (!req.body || !req.body.message || !req.body.message.data) {
       res.status(400).send('Invalid Pub/Sub message format');
       return;
    }
    const data = Buffer.from(req.body.message.data, 'base64').toString('utf8');
    await pubSubService.handleMessage(data);
    res.status(200).send('OK');
  } catch {
    console.error('PubSub RTDN processing failed');
    res.status(500).send('Retry');
  }
});

app.listen(port, () => {
  console.log(`Just-Share Backend listening on port ${port}`);
});
