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

test('fresh exact-token Play proof recovers consumed support without crediting a different receipt', () => {
  const { verifiedProductProof, hashPurchaseToken } = require('../dist/services/PlayBillingService');
  const paid = { purchaseState: 0, consumptionState: 1, acknowledgementState: 1,
    productId: 'support_developer_999', purchaseToken: 'token-B', orderId: 'order-B' };
  const proof = verifiedProductProof('support_developer_999', 'token-B', paid);
  assert.equal(proof.success, true);
  assert.equal(proof.consumed, true);
  assert.equal(proof.purchaseTokenHash, hashPurchaseToken('token-B'));
  assert.notEqual(proof.purchaseTokenHash, hashPurchaseToken('token-A'));
  assert.equal(proof.orderId, 'order-B');
  assert.equal(verifiedProductProof('support_developer_1', 'token-B', paid).success, false);
  assert.equal(verifiedProductProof('support_developer_999', 'token-A', paid).success, false);
  assert.equal(verifiedProductProof('support_developer_999', 'token-B', { ...paid, purchaseState: 2 }).success, false);
  assert.equal(verifiedProductProof('support_developer_999', 'token-B', { ...paid, purchaseState: 1 }).success, false);
  const cancelled = verifiedProductProof('support_developer_999', 'token-B', { ...paid, purchaseState: 1 });
  assert.equal(cancelled.reason, 'PURCHASE_CANCELLED');
  assert.equal(cancelled.purchaseTokenHash, hashPurchaseToken('token-B'));
  assert.throws(() => verifiedProductProof('support_developer_999', 'token-B', { purchaseState: 0 }));
});


test("live checkout rejects an unseen older order and a different app account before quota credit", () => {
  const { matchesLiveCheckout, hashPurchaseToken } = require("../dist/services/PlayBillingService");
  const account = hashPurchaseToken("device-one");
  const checkout = { startedAt: 200, account };
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "199", account }, checkout, "device-one", 300), false);
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220", account: "foreign" }, checkout, "device-one", 300), false);
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220" }, checkout, "device-one", 300), false);
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220", account }, checkout, "device-one", 300), true);
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220", account }, { startedAt: 400, account }, "device-one", 300), false);
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220" }, undefined, "device-one", 300), true); // Existing legacy restoration.
  assert.equal(matchesLiveCheckout({ purchaseTimeMillis: "220", account: "foreign" }, undefined, "device-one", 300), false);
});
