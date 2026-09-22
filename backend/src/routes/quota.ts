import { Router } from 'express';
import { QuotaService } from '../services/QuotaService';
import { deviceAuth } from '../middleware/deviceAuth';
import { appCheckAuth } from '../middleware/appCheckAuth';

const router = Router();
const quotaService = new QuotaService();

router.use(appCheckAuth, deviceAuth);

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
  } catch {
    res.status(503).json({ error: 'Quota temporarily unavailable' });
  }
});

router.post('/relay-check', async (req, res) => {
  const { deviceId, estimatedBytes } = req.body;
  if (
    deviceId !== req.header('X-Device-Id') ||
    !Number.isSafeInteger(estimatedBytes) ||
    estimatedBytes < 0
  ) {
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
  } catch {
    res.status(503).json({ error: 'Quota temporarily unavailable' });
  }
});

export default router;
