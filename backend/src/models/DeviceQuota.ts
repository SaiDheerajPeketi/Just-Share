import { Timestamp } from '@google-cloud/firestore';

export interface DeviceQuota {
  plan: 'FREE' | 'PRO';
  monthlyAllowanceGb: number;
  packBalanceGb: number;
  usedThisPeriodGb: number;
  periodStartAt: Timestamp;
  updatedAt: Timestamp;
}
