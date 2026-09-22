import { createHmac } from 'crypto';
import { Router } from 'express';
import { QuotaService } from '../services/QuotaService';
import { deviceAuth } from '../middleware/deviceAuth';
import { appCheckAuth } from '../middleware/appCheckAuth';

const router = Router();
const quotaService = new QuotaService();
const TOKEN_TTL_SECONDS = 300;

router.use(appCheckAuth, deviceAuth);

function sign(
  secret: string,
  sessionId: string,
  role: 'sender' | 'receiver',
  expiresAt: number,
  maxBytes: number
): string {
  return createHmac('sha256', Buffer.from(secret, 'hex'))
    .update(`${sessionId}:${role}:${expiresAt}:${maxBytes}`)
    .digest('hex');
}

router.post('/session-credentials', async (req, res) => {
  const headerDeviceId = req.header('X-Device-Id');
  const { deviceId, sessionId, estimatedBytes } = req.body ?? {};
  const secret = process.env.CF_RELAY_HMAC_SECRET ?? '';

  if (
    !deviceId ||
    deviceId !== headerDeviceId ||
    !/^[A-Fa-f0-9]{32}$/.test(sessionId ?? '') ||
    !Number.isSafeInteger(estimatedBytes) ||
    estimatedBytes < 0
  ) {
    res.status(400).json({ error: 'Invalid relay credential request' });
    return;
  }
  if (!/^[A-Fa-f0-9]{64}$/.test(secret)) {
    res.status(503).json({ error: 'Relay credentials are not configured' });
    return;
  }

  try {
    const reservation = await quotaService.reserveRelay(deviceId, estimatedBytes, sessionId);
    if (!reservation.allowed) {
      res.status(402).json({ error: reservation.reason ?? 'Relay quota exhausted' });
      return;
    }
    const expiresAt = Math.floor(Date.now() / 1000) + TOKEN_TTL_SECONDS;
    const maxBytes = reservation.reservedBytes;
    if (typeof maxBytes !== 'number' || !Number.isSafeInteger(maxBytes) || maxBytes <= 0) {
      res.status(503).json({ error: 'Relay reservation is unavailable' });
      return;
    }
    res.json({
      expiresAt,
      maxBytes,
      senderToken: sign(secret, sessionId, 'sender', expiresAt, maxBytes),
      receiverToken: sign(secret, sessionId, 'receiver', expiresAt, maxBytes),
    });
  } catch (error) {
    console.error('Relay credential issuance failed');
    res.status(503).json({ error: 'Relay credentials are temporarily unavailable' });
  }
});

export default router;
