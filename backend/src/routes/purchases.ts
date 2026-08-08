import { Router } from 'express';
import { PlayBillingService } from '../services/PlayBillingService';
import { QuotaService } from '../services/QuotaService';
import { deviceAuth } from '../middleware/deviceAuth';

const router = Router();
const billingService = new PlayBillingService();
const quotaService = new QuotaService();

router.use(deviceAuth);

router.post('/verify', async (req, res) => {
  const { deviceId, productId, purchaseToken } = req.body;
  if (!deviceId || !productId || !purchaseToken) {
    res.status(400).json({ error: 'Missing parameters' });
    return;
  }

  const proProductId = process.env.PRO_PRODUCT_ID || 'pro_unlock';
  const dataPackProductId = process.env.DATA_PACK_PRODUCT_ID || 'data_pack_10gb';
  const dataPackGb = Number(process.env.DATA_PACK_GB) || 10;

  try {
    const result = await billingService.verifyPurchase(deviceId, productId, purchaseToken);
    
    if (result.success) {
      if (productId === proProductId) {
        await quotaService.handleProUnlock(deviceId, true);
      } else if (productId === dataPackProductId) {
        await quotaService.handleDataPack(deviceId, true, dataPackGb);
      }

      const newQuota = await quotaService.getQuota(deviceId);
      res.status(200).json({ success: true, newQuota });
    } else {
      res.status(400).json({ error: 'Purchase verification failed', reason: result.reason });
    }
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
});

export default router;
