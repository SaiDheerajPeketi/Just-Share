import { Firestore, Timestamp } from '@google-cloud/firestore';
import { DeviceQuota } from '../models/DeviceQuota';

export class QuotaService {
  private firestore: Firestore;
  private freeTierGb: number;
  private proTierGb: number;

  constructor() {
    this.firestore = new Firestore();
    this.freeTierGb = Number(process.env.FREE_TIER_GB) || 2;
    this.proTierGb = Number(process.env.PRO_TIER_GB) || 5;
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
    const estimatedGb = estimatedBytes / (1024 * 1024 * 1024);
    const quotaRes = await this.getQuota(deviceId);
    
    if (quotaRes.remainingGb >= estimatedGb) {
      return { allowed: true };
    }
    return { allowed: false, reason: 'QUOTA_EXHAUSTED', remainingGb: quotaRes.remainingGb };
  }

  async meterRelay(deviceId: string, bytesRelayed: number, sessionId: string): Promise<{ success: boolean, reason?: string }> {
    const docRef = this.firestore.collection('deviceQuotas').doc(deviceId);
    const relayedGb = bytesRelayed / (1024 * 1024 * 1024);

    return await this.firestore.runTransaction(async (transaction) => {
      const doc = await transaction.get(docRef);
      
      if (!doc.exists) {
        throw new Error('Quota document not found');
      }

      let quota = doc.data() as DeviceQuota;
      quota = this.checkRollingPeriod(quota);

      const remainingAllowance = Math.max(0, quota.monthlyAllowanceGb - quota.usedThisPeriodGb);
      
      let deductionFromAllowance = Math.min(remainingAllowance, relayedGb);
      let deductionFromPack = relayedGb - deductionFromAllowance;

      if (deductionFromPack > quota.packBalanceGb) {
        quota.usedThisPeriodGb += deductionFromAllowance;
        quota.packBalanceGb = 0;
        quota.updatedAt = Timestamp.now();
        transaction.set(docRef, quota);
        return { success: false, reason: 'QUOTA_EXHAUSTED' };
      } else {
        quota.usedThisPeriodGb += deductionFromAllowance;
        quota.packBalanceGb -= deductionFromPack;
        quota.updatedAt = Timestamp.now();
        transaction.set(docRef, quota);
        return { success: true };
      }
    });
  }

  async handleProUnlock(deviceId: string, isUnlock: boolean): Promise<void> {
    const docRef = this.firestore.collection('deviceQuotas').doc(deviceId);
    
    await this.firestore.runTransaction(async (transaction) => {
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
      } else {
        quota = doc.data() as DeviceQuota;
        quota = this.checkRollingPeriod(quota);
      }

      if (isUnlock) {
        quota.plan = 'PRO';
        quota.monthlyAllowanceGb = this.proTierGb;
      } else {
        quota.plan = 'FREE';
        quota.monthlyAllowanceGb = this.freeTierGb;
      }
      quota.updatedAt = now;
      transaction.set(docRef, quota);
    });
  }

  async handleDataPack(deviceId: string, isAdd: boolean, packGb: number): Promise<void> {
    const docRef = this.firestore.collection('deviceQuotas').doc(deviceId);
    
    await this.firestore.runTransaction(async (transaction) => {
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
      } else {
        quota = doc.data() as DeviceQuota;
        quota = this.checkRollingPeriod(quota);
      }

      if (isAdd) {
        quota.packBalanceGb += packGb;
      } else {
        quota.packBalanceGb = Math.max(0, quota.packBalanceGb - packGb);
      }
      quota.updatedAt = now;
      transaction.set(docRef, quota);
    });
  }
}
