import { Router } from 'express';
import { QuotaService } from '../services/QuotaService';
import { deviceAuth } from '../middleware/deviceAuth';

const router = Router();
const quotaService = new QuotaService();

router.use(deviceAuth);

router.get('/:deviceId', async (req, res) => {
  const { deviceId } = req.params;
  const headerDeviceId = req.header('X-Device-Id');
  if (deviceId !== headerDeviceId) {
    res.status(403).json({ error: 'Device ID mismatch' });
    return;
  }
  
  try {
    const quota = await quotaService.getQuota(deviceId);
    res.json(quota);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/relay-check', async (req, res) => {
  const { deviceId, estimatedBytes } = req.body;
  if (!deviceId || typeof estimatedBytes !== 'number') {
    res.status(400).json({ error: 'Missing deviceId or estimatedBytes' });
    return;
  }

  try {
    const result = await quotaService.checkRelay(deviceId, estimatedBytes);
    if (result.allowed) {
      res.json(result);
    } else {
      res.status(402).json(result);
    }
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

router.post('/relay-meter', async (req, res) => {
  const { deviceId, bytesRelayed, sessionId } = req.body;
  if (!deviceId || typeof bytesRelayed !== 'number' || !sessionId) {
    res.status(400).json({ error: 'Missing parameters' });
    return;
  }

  try {
    const result = await quotaService.meterRelay(deviceId, bytesRelayed, sessionId);
    if (result.success) {
      res.status(200).json(result);
    } else {
      res.status(402).json(result);
    }
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

export default router;
