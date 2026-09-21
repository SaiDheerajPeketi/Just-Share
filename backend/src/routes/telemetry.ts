import { Router } from 'express';
import { Firestore, FieldValue } from '@google-cloud/firestore';
import { deviceAuth } from '../middleware/deviceAuth';
import { appCheckAuth } from '../middleware/appCheckAuth';

const router = Router();
const firestore = new Firestore();

router.use(appCheckAuth, deviceAuth);

router.post('/', async (req, res) => {
  const { deviceId, event, bytes, connectionMode } = req.body;
  
  if (deviceId !== req.header('X-Device-Id') || typeof event !== 'string') {
    res.status(200).json({ success: true });
    return;
  }

  const allowedEvents = new Set([
    'relay_session_started',
    'relay_session_completed',
    'direct_session_completed',
    'quota_exhausted',
    'pack_purchased',
    'pro_purchased',
  ]);
  if (!allowedEvents.has(event)) {
    res.status(200).json({ success: true });
    return;
  }

  try {
    const dateStr = new Date().toISOString().split('T')[0];
    const docRef = firestore.collection('telemetry').doc(`${dateStr}_${event}`);
    
    await docRef.set({
      event,
      date: dateStr,
      count: FieldValue.increment(1),
      totalBytes: Number.isFinite(bytes) && bytes > 0
        ? FieldValue.increment(Math.min(bytes, 5_368_709_120))
        : FieldValue.increment(0),
    }, { merge: true });
    
  } catch (error) {
    console.error('Telemetry error:', error);
  }

  res.status(200).json({ success: true });
});

export default router;
