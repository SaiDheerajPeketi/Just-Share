import { getApps, initializeApp } from 'firebase-admin/app';
import { getAppCheck } from 'firebase-admin/app-check';
import { NextFunction, Request, Response } from 'express';

const firebaseApp = getApps()[0] ?? initializeApp();

/**
 * Rejects API traffic that did not originate from an attested Just Share app.
 * The device id remains a pseudonymous quota key; it is not authentication.
 */
export async function appCheckAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  const expectedAppId = process.env.FIREBASE_APP_ID;
  const token = req.header('X-Firebase-AppCheck') ?? '';

  if (!expectedAppId) {
    res.status(503).json({ error: 'App attestation is not configured' });
    return;
  }
  if (!token) {
    res.status(401).json({ error: 'Missing app attestation' });
    return;
  }

  try {
    const decoded = await getAppCheck(firebaseApp).verifyToken(token);
    if (decoded.appId !== expectedAppId) {
      res.status(403).json({ error: 'Invalid app identity' });
      return;
    }
    next();
  } catch {
    res.status(401).json({ error: 'Invalid app attestation' });
  }
}
