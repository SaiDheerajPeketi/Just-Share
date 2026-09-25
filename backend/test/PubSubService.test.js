const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { test } = require('node:test');
const { PubSubService } = require('../dist/services/PubSubService');

const packageName = process.env.ANDROID_PACKAGE_NAME || 'com.blackandblue.justshare';

function subject() {
  const calls = [];
  const service = new PubSubService({
    async revokeVerifiedPurchase(...args) {
      calls.push(args);
    },
  });
  return { service, calls };
}

test('full void of a Just Share one-time purchase revokes its recorded benefit', async () => {
  const { service, calls } = subject();
  await service.handleMessage(JSON.stringify({
    packageName,
    voidedPurchaseNotification: {
      purchaseToken: 'purchased-token',
      productType: 2,
      refundType: 1,
    },
  }));

  assert.equal(calls.length, 1);
  assert.equal(calls[0][0], createHash('sha256').update('purchased-token').digest('hex'));
});

test('canceled pending purchases do not revoke a completed purchase', async () => {
  const { service, calls } = subject();
  await service.handleMessage(JSON.stringify({
    packageName,
    oneTimeProductNotification: {
      notificationType: 2,
      purchaseToken: 'pending-token',
      sku: 'pro_unlock',
    },
  }));
  assert.equal(calls.length, 0);
});

test('other apps, subscriptions, and partial refunds do not revoke a one-time benefit', async () => {
  const { service, calls } = subject();
  for (const [app, productType, refundType] of [
    ['com.blackandblue.anotherapp', 2, 1],
    [packageName, 1, 1],
    [packageName, 2, 2],
  ]) {
    await service.handleMessage(JSON.stringify({
      packageName: app,
      voidedPurchaseNotification: {
        purchaseToken: 'purchased-token',
        productType,
        refundType,
      },
    }));
  }
  assert.equal(calls.length, 0);
});
