import { QuotaService } from './QuotaService';
import { Firestore } from '@google-cloud/firestore';

export class PubSubService {
  private quotaService: QuotaService;
  private firestore: Firestore;

  constructor() {
    this.quotaService = new QuotaService();
    this.firestore = new Firestore();
  }

  async handleMessage(data: string): Promise<void> {
    const payload = JSON.parse(data);
    
    if (payload.oneTimeProductNotification) {
      const notification = payload.oneTimeProductNotification;
      const purchaseToken = notification.purchaseToken;
      
      const doc = await this.firestore.collection('purchaseRecords').doc(purchaseToken).get();
      if (!doc.exists) {
        console.warn(`No purchase record found for token ${purchaseToken}`);
        return;
      }
      
      const record = doc.data()!;
      const { deviceId, productId } = record;
      const proProductId = process.env.PRO_PRODUCT_ID || 'pro_unlock';
      const dataPackProductId = process.env.DATA_PACK_PRODUCT_ID || 'data_pack_10gb';
      const dataPackGb = Number(process.env.DATA_PACK_GB) || 10;

      // 1: ONE_TIME_PRODUCT_PURCHASED, 2: ONE_TIME_PRODUCT_CANCELED (refund)
      if (notification.notificationType === 2) { 
        if (productId === proProductId) {
          await this.quotaService.handleProUnlock(deviceId, false);
        } else if (productId === dataPackProductId) {
          await this.quotaService.handleDataPack(deviceId, false, dataPackGb);
        }
      }
    }
  }
}
