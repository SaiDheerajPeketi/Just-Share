import { google } from 'googleapis';
import { createHash } from 'crypto';

export function hashPurchaseToken(purchaseToken: string): string {
  return createHash('sha256').update(purchaseToken).digest('hex');
}

export function verifiedProductProof(productId: string, purchaseToken: string, purchase: {
  purchaseState?: number | null; consumptionState?: number | null; acknowledgementState?: number | null;
  productId?: string | null; purchaseToken?: string | null; orderId?: string | null;
}) {
  if ((purchase.productId && purchase.productId !== productId) ||
      (purchase.purchaseToken && purchase.purchaseToken !== purchaseToken)) {
    return { success: false, reason: 'PURCHASE_MISMATCH' };
  }
  if (purchase.purchaseState !== 0) return {
    success: false, reason: purchase.purchaseState === 1 ? 'PURCHASE_CANCELLED' : 'NOT_PURCHASED',
    productId, purchaseTokenHash: hashPurchaseToken(purchaseToken),
  };
  if (![0, 1].includes(purchase.consumptionState as number) ||
      ![0, 1].includes(purchase.acknowledgementState as number)) {
    throw new Error('PLAY_FINALIZATION_STATE_UNAVAILABLE');
  }
  return {
    success: true, productId, orderId: purchase.orderId || undefined,
    purchaseTokenHash: hashPurchaseToken(purchaseToken),
    consumed: purchase.consumptionState === 1, acknowledged: purchase.acknowledgementState === 1,
  };
}

export class PlayBillingService {
  private packageName: string;

  constructor() {
    this.packageName = process.env.ANDROID_PACKAGE_NAME || 'com.blackandblue.justshare';
  }

  private async getAndroidPublisher() {
    const auth = new google.auth.GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/androidpublisher']
    });
    return google.androidpublisher({ version: 'v3', auth });
  }

  async verifyPurchase(productId: string, purchaseToken: string): Promise<any> {
    const publisher = await this.getAndroidPublisher();
    
    try {
      const response = await publisher.purchases.products.get({
        packageName: this.packageName,
        productId,
        token: purchaseToken,
      });

      return verifiedProductProof(productId, purchaseToken, response.data);
    } catch {
      console.error('Play purchase verification failed');
      throw new Error('PLAY_VERIFICATION_FAILED');
    }
  }
}
