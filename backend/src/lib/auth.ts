import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import type { FastifyReply, FastifyRequest } from 'fastify';
import { config } from '../config.js';
import { redis } from './redis.js';

const ACCESS_TTL_SECONDS = 60 * 60;            // 1 saat
const REFRESH_TTL_SECONDS = 60 * 60 * 24 * 90; // 90 gün

export interface AccessPayload {
  sub: string; // userId
}

declare module 'fastify' {
  interface FastifyRequest {
    userId?: string;
  }
}

export function issueAccessToken(userId: string): string {
  return jwt.sign({ sub: userId } satisfies AccessPayload, config.JWT_SECRET, {
    expiresIn: ACCESS_TTL_SECONDS,
    issuer: 'dersdefteri',
  });
}

/// Yenileme jetonu bilinçli olarak JWT değil: Redis'te tutulan opak bir değer,
/// böylece hesap silindiğinde veya çıkış yapıldığında anında iptal edilebilir.
export async function issueRefreshToken(userId: string): Promise<string> {
  const token = crypto.randomBytes(32).toString('base64url');
  await redis.set(`refresh:${token}`, userId, 'EX', REFRESH_TTL_SECONDS);
  await redis.sadd(`refresh:user:${userId}`, token);
  await redis.expire(`refresh:user:${userId}`, REFRESH_TTL_SECONDS);
  return token;
}

export async function consumeRefreshToken(token: string): Promise<string | null> {
  const userId = await redis.get(`refresh:${token}`);
  if (!userId) return null;
  // Tek kullanımlık: her tazelemede yeni jeton verilir, çalınan jeton bir kez
  // kullanıldıktan sonra işe yaramaz.
  await redis.del(`refresh:${token}`);
  await redis.srem(`refresh:user:${userId}`, token);
  return userId;
}

export async function revokeAllRefreshTokens(userId: string): Promise<void> {
  const tokens = await redis.smembers(`refresh:user:${userId}`);
  if (tokens.length > 0) {
    await redis.del(...tokens.map((t) => `refresh:${t}`));
  }
  await redis.del(`refresh:user:${userId}`);
}

export const accessTokenTTL = ACCESS_TTL_SECONDS;

/// Korumalı uçlara `preHandler` olarak takılır.
export async function requireAuth(request: FastifyRequest, reply: FastifyReply): Promise<void> {
  const header = request.headers.authorization;
  if (!header?.startsWith('Bearer ')) {
    return reply.code(401).send({ error: 'unauthorized', message: 'Oturum bulunamadı.' });
  }

  try {
    const decoded = jwt.verify(header.slice(7), config.JWT_SECRET, {
      issuer: 'dersdefteri',
    }) as AccessPayload;
    request.userId = decoded.sub;
  } catch {
    return reply.code(401).send({ error: 'token_expired', message: 'Oturumun süresi doldu.' });
  }
}
