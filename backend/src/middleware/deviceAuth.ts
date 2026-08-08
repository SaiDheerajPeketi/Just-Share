import { Request, Response, NextFunction } from 'express';

export function deviceAuth(req: Request, res: Response, next: NextFunction): void {
  const deviceId = req.header('X-Device-Id');
  if (!deviceId || typeof deviceId !== 'string' || deviceId.trim() === '') {
    res.status(400).json({ error: 'Missing or invalid X-Device-Id header' });
    return;
  }
  next();
}
