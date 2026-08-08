import dotenv from 'dotenv';
dotenv.config();

import express from 'express';
import quotaRoutes from './routes/quota';
import purchaseRoutes from './routes/purchases';
import telemetryRoutes from './routes/telemetry';
import { PubSubService } from './services/PubSubService';

const app = express();
const port = process.env.PORT || 8080;

app.use(express.json());

app.use('/quota', quotaRoutes);
app.use('/purchase', purchaseRoutes);
app.use('/telemetry', telemetryRoutes);

const pubSubService = new PubSubService();

app.post('/pubsub/rtdn', async (req, res) => {
  try {
    if (!req.body || !req.body.message || !req.body.message.data) {
       res.status(400).send('Invalid Pub/Sub message format');
       return;
    }
    const data = Buffer.from(req.body.message.data, 'base64').toString('utf8');
    await pubSubService.handleMessage(data);
    res.status(200).send('OK');
  } catch (error) {
    console.error('PubSub RTDN error:', error);
    res.status(200).send('Error handled'); 
  }
});

app.listen(port, () => {
  console.log(`Just-Share Backend listening on port ${port}`);
});
