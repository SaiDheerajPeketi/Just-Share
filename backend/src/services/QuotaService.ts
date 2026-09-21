import { Firestore, Timestamp } from '@google-cloud/firestore';
import { DeviceQuota } from '../models/DeviceQuota';
import { PurchaseRecord } from '../models/PurchaseRecord';

interface VerifiedPurchaseInput {
  deviceId: string;
  productId: string;
  purchaseTokenHash: string;
  orderId?: string;
  proProductId: string;
  dataPackProductId: string;
  dataPackGb: number;
}

export class QuotaService {
  private firestore: Firestore;
  private freeTierGb: number;
  private proTierGb: number;
  private maxRelaySessionBytes: number;

  constructor() {
    this.firestore = new Firestore();
    this.freeTierGb = Number(process.env.FREE_TIER_GB) || 2;
    this.proTierGb = Number(process.env.PRO_TIER_GB) || 5;
    this.maxRelaySessionBytes = Number(process.env.MAX_RELAY_SESSION_BYTES) || 5_368_709_120;
  }

  private checkRollingPeriod(quota: DeviceQuota): DeviceQuota {
    const now = Timestamp.now();
    const periodStartMillis = quota.periodStartAt.toMillis();
    const thirtyDaysMillis = 30 * 24 * 60 * 60 * 1000;

    if (now.toMillis() - periodStartMillis > thirtyDaysMillis) {
      return {
        ...quota,
        usedThisPeriodGb: 0,
        periodStartAt: now,
        updatedAt: now,
      };
    }
    return quota;
  }

  async getQuota(deviceId: string): Promise<any> {
    const docRef = this.firestore.collection('deviceQuotas').doc(deviceId);
    
    return await this.firestore.runTransaction(async (transaction) => {
      const doc = await transaction.get(docRef);
      
      let quota: DeviceQuota;
      const now = Timestamp.now();
      
      if (!doc.exists) {
        quota = {
          plan: 'FREE',
          monthlyAllowanceGb: this.freeTierGb,
          packBalanceGb: 0,
          usedThisPeriodGb: 0,
          periodStartAt: now,
          updatedAt: now,
        };
        transaction.set(docRef, quota);
      } else {
        quota = doc.data() as DeviceQuota;
        const updatedQuota = this.checkRollingPeriod(quota);
        if (updatedQuota.periodStartAt.toMillis() !== quota.periodStartAt.toMillis()) {
          transaction.set(docRef, updatedQuota);
          quota = updatedQuota;
        }
      }

      const remainingGb = Math.max(0, quota.monthlyAllowanceGb - quota.usedThisPeriodGb) + quota.packBalanceGb;

      return {
        plan: quota.plan,
        monthlyAllowanceGb: quota.monthlyAllowanceGb,
        packBalanceGb: quota.packBalanceGb,
        usedThisPeriodGb: quota.usedThisPeriodGb,
        remainingGb,
        periodResetAt: new Date(quota.periodStartAt.toMillis() + 30 * 24 * 60 * 60 * 1000).toISOString(),
      };
    });
  }

  async checkRelay(deviceId: string, estimatedBytes: number): Promise<{ allowed: boolean, reason?: string, remainingGb?: number }> {
    const normalizedBytes = estimatedBytes > 0 ? estimatedBytes : this.maxRelaySessionBytes;
    if (normalizedBytes > this.maxRelaySessionBytes) {
      return { allowed: false, reason: 'SESSION_TOO_LARGE' };
    }
    const estimatedGb = normalizedBytes / (1024 * 1024 * 1024);
    const quotaRes = await this.getQuota(deviceId);
    
    if (quotaRes.remainingGb >= estimatedGb) {
      return { allowed: true };
    }
    return { allowed: false, reason: 'QUOTA_EXHAUSTED', remainingGb: quotaRes.remainingGb };
  }

  async reserveRelay(
    deviceId: string,
    estimatedBytes: number,
    sessionId: string
  ): Promise<{ allowed: boolean, reason?: string, reservedBytes?: number }> {
    const docRef = this.firestore.collection('deviceQuotas').doc(deviceId);
    const sessionRef = this.firestore.collection('relaySessions').doc(sessionId);
    const reservedBytes = estimatedBytes > 0 ? estimatedBytes : this.maxRelaySessionBytes;
    if (reservedBytes > this.maxRelaySessionBytes) {
      return { allowed: false, reason: 'SESSION_TOO_LARGE' };
    }
    const reservedGb = reservedBytes / (1024 * 1024 * 1024);

    return await this.firestore.runTransaction(async (transaction) => {
      const sessionDoc = await transaction.get(sessionRef);
      const doc = await transaction.get(docRef);

      if (sessionDoc.exists) {
        const existing = sessionDoc.data();
        return existing?.deviceId === deviceId
          ? { allowed: true, reservedBytes: Number(existing.reservedBytes) }
          : { allowed: false, reason: 'SESSION_ALREADY_CLAIMED' };
      }

      if (!doc.exists) {
        const now = Timestamp.now();
        transaction.set(docRef, {
          plan: 'FREE',
          monthlyAllowanceGb: this.freeTierGb,
          packBalanceGb: 0,
          usedThisPeriodGb: 0,
          periodStartAt: now,
          updatedAt: now,
        });
        return { allowed: false, reason: 'QUOTA_REFRESH_REQUIRED' };
      }

      let quota = doc.data() as DeviceQuota;
      quota = this.checkRollingPeriod(quota);

      const remainingAllowance = Math.max(0, quota.monthlyAllowanceGb - quota.usedThisPeriodGb);
      const deductionFromAllowance = Math.min(remainingAllowance, reservedGb);
      const deductionFromPack = reservedGb - deductionFromAllowance;
      if (deductionFromPack > quota.packBalanceGb) {
        return { allowed: false, reason: 'QUOTA_EXHAUSTED' };
      }

      const now = Timestamp.now();
      quota.usedThisPeriodGb += deductionFromAllowance;
      quota.packBalanceGb -= deductionFromPack;
      quota.updatedAt = now;
      transaction.set(docRef, quota);
      transaction.create(sessionRef, {
        deviceId,
        reservedBytes,
        createdAt: now,
        expiresAt: Timestamp.fromMillis(now.toMillis() + 24 * 60 * 60 * 1000),
      });
      return { allowed: true, reservedBytes };
    });
  }

  async applyVerifiedPurchase(input: VerifiedPurchaseInput): Promise<{ applied: boolean }> {
    const quotaRef = this.firestore.collection('deviceQuotas').doc(input.deviceId);
    const purchaseRef = this.firestore.collection('purchaseRecords').doc(input.purchaseTokenHash);

    return this.firestore.runTransaction(async (transaction) => {
      const purchaseDoc = await transaction.get(purchaseRef);
      const quotaDoc = await transaction.get(quotaRef);
      if (purchaseDoc.exists) {
        const existing = purchaseDoc.data() as PurchaseRecord;
        if (existing.deviceId !== input.deviceId || existing.productId !== input.productId) {
          throw new Error('PURCHASE_ALREADY_CLAIMED');
        }
        return { applied: false };
      }

      const now = Timestamp.now();
      let quota: DeviceQuota = quotaDoc.exists
        ? this.checkRollingPeriod(quotaDoc.data() as DeviceQuota)
        : {
            plan: 'FREE',
            monthlyAllowanceGb: this.freeTierGb,
            packBalanceGb: 0,
            usedThisPeriodGb: 0,
            periodStartAt: now,
            updatedAt: now,
          };

      if (input.productId === input.proProductId) {
        quota.plan = 'PRO';
        quota.monthlyAllowanceGb = this.proTierGb;
      } else if (input.productId === input.dataPackProductId) {
        quota.packBalanceGb += input.dataPackGb;
      } else {
        throw new Error('UNKNOWN_PRODUCT');
      }
      quota.updatedAt = now;

      const record: PurchaseRecord = {
        deviceId: input.deviceId,
        productId: input.productId,
        purchaseTokenHash: input.purchaseTokenHash,
        verifiedAt: now,
        benefitAppliedAt: now,
        ...(input.orderId ? { orderId: input.orderId } : {}),
      };
      transaction.set(quotaRef, quota);
      transaction.create(purchaseRef, record);
      return { applied: true };
    });
  }

  async revokeVerifiedPurchase(
    purchaseTokenHash: string,
    proProductId: string,
    dataPackProductId: string,
    dataPackGb: number
  ): Promise<void> {
    const purchaseRef = this.firestore.collection('purchaseRecords').doc(purchaseTokenHash);
    await this.firestore.runTransaction(async (transaction) => {
      const purchaseDoc = await transaction.get(purchaseRef);
      if (!purchaseDoc.exists) return;
      const record = purchaseDoc.data() as PurchaseRecord;
      if (record.revokedAt) return;

      const quotaRef = this.firestore.collection('deviceQuotas').doc(record.deviceId);
      const quotaDoc = await transaction.get(quotaRef);
      const now = Timestamp.now();
      if (quotaDoc.exists) {
        const quota = this.checkRollingPeriod(quotaDoc.data() as DeviceQuota);
        if (record.productId === proProductId) {
          quota.plan = 'FREE';
          quota.monthlyAllowanceGb = this.freeTierGb;
        } else if (record.productId === dataPackProductId) {
          quota.packBalanceGb = Math.max(0, quota.packBalanceGb - dataPackGb);
        }
        quota.updatedAt = now;
        transaction.set(quotaRef, quota);
      }
      transaction.update(purchaseRef, { revokedAt: now });
    });
  }

}
