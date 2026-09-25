import { QuotaService } from './QuotaService';
import { hashPurchaseToken } from './PlayBillingService';

type RevocationStore = Pick<QuotaService, 'revokeVerifiedPurchase'>;

export class PubSubService {
  private quotaService: RevocationStore;

  constructor(quotaService: RevocationStore = new QuotaService()) {
    this.quotaService = quotaService;
  }

  async handleMessage(data: string): Promise<void> {
    const payload = JSON.parse(data);
    if (payload?.packageName !== (process.env.ANDROID_PACKAGE_NAME || 'com.blackandblue.justshare')) {
      return;
    }

    // ONE_TIME_PRODUCT_CANCELED is a canceled pending purchase, not a refund.
    // A completed one-time purchase is revoked only by a full void notification.
    const notification = payload.voidedPurchaseNotification;
    if (
      notification?.productType !== 2 ||
      notification.refundType !== 1 ||
      typeof notification.purchaseToken !== 'string' ||
      !notification.purchaseToken
    ) return;

    await this.quotaService.revokeVerifiedPurchase(
      hashPurchaseToken(notification.purchaseToken),
      process.env.PRO_PRODUCT_ID || 'pro_unlock',
      process.env.DATA_PACK_PRODUCT_ID || 'data_pack_10gb',
      Number(process.env.DATA_PACK_GB) || 10
    );
  }
}
