import { Router } from 'express';
import { PlayBillingService } from '../services/PlayBillingService';
import { QuotaService } from '../services/QuotaService';
import { deviceAuth } from '../middleware/deviceAuth';
import { appCheckAuth } from '../middleware/appCheckAuth';

const router = Router();
const billingService = new PlayBillingService();
const quotaService = new QuotaService();

router.use(appCheckAuth, deviceAuth);

router.post('/verify', async (req, res) => {
  const { deviceId, productId, purchaseToken } = req.body;
  if (
    deviceId !== req.header('X-Device-Id') ||
    typeof productId !== 'string' ||
    typeof purchaseToken !== 'string' ||
    purchaseToken.length < 16 ||
    purchaseToken.length > 4096
  ) {
    res.status(400).json({ error: 'Missing parameters' });
    return;
  }

  const proProductId = process.env.PRO_PRODUCT_ID || 'pro_unlock';
  const dataPackProductId = process.env.DATA_PACK_PRODUCT_ID || 'data_pack_10gb';
  const supportTipProductId = process.env.SUPPORT_TIP_PRODUCT_ID || 'student_developer_tip';
  const dataPackGb = Number(process.env.DATA_PACK_GB) || 10;
  if (
    productId !== proProductId &&
    productId !== dataPackProductId &&
    productId !== supportTipProductId
  ) {
    res.status(400).json({ error: 'Unknown product' });
    return;
  }

  try {
    const result = await billingService.verifyPurchase(productId, purchaseToken);
    
    if (result.success) {
      await quotaService.applyVerifiedPurchase({
        deviceId,
        productId,
        purchaseTokenHash: result.purchaseTokenHash,
        orderId: result.orderId,
        proProductId,
        dataPackProductId,
        supportTipProductId,
        dataPackGb,
      });

      const newQuota = await quotaService.getQuota(deviceId);
      res.status(200).json({ success: true, newQuota });
    } else {
      res.status(400).json({ error: 'Purchase verification failed', reason: result.reason });
    }
  } catch {
    res.status(502).json({ error: 'Purchase verification temporarily unavailable' });
  }
});

export default router;
