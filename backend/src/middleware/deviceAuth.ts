import { Request, Response, NextFunction } from 'express';

export function deviceAuth(req: Request, res: Response, next: NextFunction): void {
  const deviceId = req.header('X-Device-Id');
  const uuidV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  if (!deviceId || !uuidV4.test(deviceId)) {
    res.status(400).json({ error: 'Missing or invalid X-Device-Id header' });
    return;
  }
  next();
}
