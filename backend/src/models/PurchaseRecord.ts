import { Timestamp } from '@google-cloud/firestore';

export interface PurchaseRecord {
  deviceId: string;
  productId: string;
  purchaseTokenHash: string;
  verifiedAt: Timestamp;
  benefitAppliedAt: Timestamp;
  orderId?: string;
  revokedAt?: Timestamp;
}
