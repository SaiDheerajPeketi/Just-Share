import { google } from 'googleapis';
import { createHash } from 'crypto';

export function hashPurchaseToken(purchaseToken: string): string {
  return createHash('sha256').update(purchaseToken).digest('hex');
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

      const purchase = response.data;
      
      if (purchase.purchaseState === 0) { // 0 = PURCHASED
        return {
          success: true,
          orderId: purchase.orderId || undefined,
          purchaseTokenHash: hashPurchaseToken(purchaseToken),
        };
      }
      
      return { success: false, reason: 'NOT_PURCHASED' };
    } catch {
      console.error('Play purchase verification failed');
      throw new Error('PLAY_VERIFICATION_FAILED');
    }
  }
}
