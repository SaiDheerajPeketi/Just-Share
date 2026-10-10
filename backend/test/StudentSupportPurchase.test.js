const assert = require('node:assert/strict');
const { test } = require('node:test');
const { Timestamp } = require('@google-cloud/firestore');
const { SUPPORT_TIP_PRODUCT_IDS } = require('../dist/productCatalog');
const { QuotaService } = require('../dist/services/QuotaService');

test('fourth support tier records repeat tokens idempotently without granting paid quota', async () => {
  assert.deepEqual(SUPPORT_TIP_PRODUCT_IDS, [
    'support_developer_1', 'support_developer_10', 'support_developer_100', 'support_developer_999',
  ]);
  const now = Timestamp.now();
  const quotas = new Map([['deviceQuotas/device-one', {
    plan: 'FREE', monthlyAllowanceGb: 2, packBalanceGb: 0, usedThisPeriodGb: 0.25,
    periodStartAt: now, updatedAt: now,
  }]]);
  const service = new QuotaService();
  service.firestore = {
    collection(name) { return { doc(id) { return { path: `${name}/${id}` }; } }; },
    async runTransaction(work) {
      return work({
        async get(ref) {
          const value = quotas.get(ref.path);
          return { exists: value !== undefined, data() { return value && { ...value }; } };
        },
        set(ref, value) { quotas.set(ref.path, { ...value }); },
        create(ref, value) {
          assert.equal(quotas.has(ref.path), false);
          quotas.set(ref.path, { ...value });
        },
      });
    },
  };
  const input = {
    deviceId: 'device-one', productId: 'support_developer_999', purchaseTokenHash: 'token-first',
    proProductId: 'pro_unlock', dataPackProductId: 'data_pack_10gb',
    supportTipProductIds: SUPPORT_TIP_PRODUCT_IDS, dataPackGb: 10,
  };
  assert.deepEqual(await service.applyVerifiedPurchase(input), { applied: true });
  assert.deepEqual(await service.applyVerifiedPurchase(input), { applied: false });
  assert.deepEqual(await service.applyVerifiedPurchase({ ...input, purchaseTokenHash: 'token-repeat' }), { applied: true });
  const quota = quotas.get('deviceQuotas/device-one');
  assert.equal(quota.plan, 'FREE');
  assert.equal(quota.monthlyAllowanceGb, 2);
  assert.equal(quota.packBalanceGb, 0);
  assert.equal(quota.usedThisPeriodGb, 0.25);
  assert.equal(quotas.size, 3);
});
