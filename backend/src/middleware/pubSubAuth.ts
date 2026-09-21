import { Request, Response, NextFunction } from 'express';
import { OAuth2Client } from 'google-auth-library';

const auth = new OAuth2Client();

export async function pubSubAuth(req: Request, res: Response, next: NextFunction): Promise<void> {
  const audience = process.env.PUBSUB_PUSH_AUDIENCE;
  const expectedServiceAccount = process.env.PUBSUB_PUSH_SERVICE_ACCOUNT;
  const authorization = req.header('Authorization') ?? '';
  const idToken = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';

  if (!audience || !expectedServiceAccount || !idToken) {
    res.status(401).send('Unauthorized');
    return;
  }

  try {
    const ticket = await auth.verifyIdToken({ idToken, audience });
    const payload = ticket.getPayload();
    if (payload?.email !== expectedServiceAccount || payload.email_verified !== true) {
      res.status(403).send('Forbidden');
      return;
    }
    next();
  } catch {
    res.status(401).send('Unauthorized');
  }
}
