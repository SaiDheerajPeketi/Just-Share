import { google } from 'googleapis';
import { Firestore, Timestamp } from '@google-cloud/firestore';
import { PurchaseRecord } from '../models/PurchaseRecord';

export class PlayBillingService {
  private firestore: Firestore;
  private packageName: string;

  constructor() {
    this.firestore = new Firestore();
    this.packageName = process.env.ANDROID_PACKAGE_NAME || 'com.invincible.jedishare';
  }

  private async getAndroidPublisher() {
    const auth = new google.auth.GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/androidpublisher']
    });
    const client = await auth.getClient();
    return google.androidpublisher({ version: 'v3', auth: client });
  }

  async verifyPurchase(deviceId: string, productId: string, purchaseToken: string): Promise<any> {
    const publisher = await this.getAndroidPublisher();
    
    try {
      const response = await publisher.purchases.products.get({
        packageName: this.packageName,
        productId,
        token: purchaseToken,
      });

      const purchase = response.data;
      
      if (purchase.purchaseState === 0) { // 0 = PURCHASED
        const record: PurchaseRecord = {
          deviceId,
          productId,
          purchaseToken,
          verifiedAt: Timestamp.now(),
          orderId: purchase.orderId || undefined,
        };
        
        await this.firestore.collection('purchaseRecords').doc(purchaseToken).set(record);
        return { success: true, orderId: purchase.orderId };
      }
      
      return { success: false, reason: 'NOT_PURCHASED' };
    } catch (error: any) {
      console.error('Play API verify error:', error);
      throw error;
    }
  }
}
