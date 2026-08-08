import { Router } from 'express';
import { Firestore, FieldValue } from '@google-cloud/firestore';
import { deviceAuth } from '../middleware/deviceAuth';

const router = Router();
const firestore = new Firestore();

router.use(deviceAuth);

router.post('/', async (req, res) => {
  const { deviceId, event, bytes, connectionMode } = req.body;
  
  if (!deviceId || !event) {
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
      totalBytes: bytes ? FieldValue.increment(bytes) : FieldValue.increment(0),
    }, { merge: true });
    
  } catch (error) {
    console.error('Telemetry error:', error);
  }

  res.status(200).json({ success: true });
});

export default router;
