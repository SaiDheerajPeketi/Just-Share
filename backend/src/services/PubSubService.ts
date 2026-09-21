import { QuotaService } from './QuotaService';
import { hashPurchaseToken } from './PlayBillingService';

export class PubSubService {
  private quotaService: QuotaService;

  constructor() {
    this.quotaService = new QuotaService();
  }

  async handleMessage(data: string): Promise<void> {
    const payload = JSON.parse(data);
    
    if (payload.oneTimeProductNotification) {
      const notification = payload.oneTimeProductNotification;
      const purchaseToken = notification.purchaseToken;
      const proProductId = process.env.PRO_PRODUCT_ID || 'pro_unlock';
      const dataPackProductId = process.env.DATA_PACK_PRODUCT_ID || 'data_pack_10gb';
      const dataPackGb = Number(process.env.DATA_PACK_GB) || 10;

      // 1: ONE_TIME_PRODUCT_PURCHASED, 2: ONE_TIME_PRODUCT_CANCELED (refund)
      if (notification.notificationType === 2 && typeof purchaseToken === 'string') {
        await this.quotaService.revokeVerifiedPurchase(
          hashPurchaseToken(purchaseToken),
          proProductId,
          dataPackProductId,
          dataPackGb
        );
      }
    }
  }
}
