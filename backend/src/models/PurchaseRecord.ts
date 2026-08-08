import { Timestamp } from '@google-cloud/firestore';

export interface PurchaseRecord {
  deviceId: string;
  productId: string;
  purchaseToken: string;
  verifiedAt: Timestamp;
  orderId?: string;
}
