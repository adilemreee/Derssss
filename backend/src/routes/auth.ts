import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma.js';
import { verifyAppleIdentityToken } from '../lib/appleAuth.js';
import {
  accessTokenTTL,
  consumeRefreshToken,
  issueAccessToken,
  issueRefreshToken,
  requireAuth,
  revokeAllRefreshTokens,
} from '../lib/auth.js';

const appleSignInBody = z.object({
  identityToken: z.string().min(1),
  rawNonce: z.string().optional(),
  /// Apple tam adı yalnızca ilk girişte gönderir; sonraki girişlerde gelmez.
  fullName: z.string().max(120).optional(),
});

const refreshBody = z.object({
  refreshToken: z.string().min(1),
});

export async function authRoutes(app: FastifyInstance) {
  app.post('/v1/auth/apple', {
    config: { rateLimit: { max: 20, timeWindow: '5 minutes' } },
  }, async (request, reply) => {
    const parsed = appleSignInBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_body', message: 'Giriş bilgileri eksik.' });
    }

    let identity;
    try {
      identity = await verifyAppleIdentityToken(parsed.data.identityToken, parsed.data.rawNonce);
    } catch (err) {
      request.log.warn({ err }, 'apple identity token doğrulanamadı');
      return reply.code(401).send({ error: 'invalid_token', message: 'Apple girişi doğrulanamadı.' });
    }

    const user = await prisma.user.upsert({
      where: { appleSub: identity.sub },
      create: {
        appleSub: identity.sub,
        email: identity.email ?? null,
        name: parsed.data.fullName ?? null,
      },
      update: {
        lastSeenAt: new Date(),
        // Apple bu alanları yalnız ilk girişte gönderir; boş gelen değer
        // kayıtlı bilgiyi silmemeli.
        ...(identity.email ? { email: identity.email } : {}),
        ...(parsed.data.fullName ? { name: parsed.data.fullName } : {}),
      },
    });

    const refreshToken = await issueRefreshToken(user.id);
    return reply.send({
      accessToken: issueAccessToken(user.id),
      refreshToken,
      expiresIn: accessTokenTTL,
      user: { id: user.id, email: user.email, name: user.name },
    });
  });

  app.post('/v1/auth/refresh', {
    config: { rateLimit: { max: 60, timeWindow: '5 minutes' } },
  }, async (request, reply) => {
    const parsed = refreshBody.safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_body', message: 'Yenileme jetonu eksik.' });
    }

    const userId = await consumeRefreshToken(parsed.data.refreshToken);
    if (!userId) {
      return reply.code(401).send({ error: 'invalid_refresh', message: 'Oturum sona erdi.' });
    }

    // Kullanıcı silinmişse jeton geçerli olsa bile oturum açılmamalı.
    const user = await prisma.user.findUnique({ where: { id: userId }, select: { id: true } });
    if (!user) {
      return reply.code(401).send({ error: 'invalid_refresh', message: 'Oturum sona erdi.' });
    }

    return reply.send({
      accessToken: issueAccessToken(userId),
      refreshToken: await issueRefreshToken(userId),
      expiresIn: accessTokenTTL,
    });
  });

  app.post('/v1/auth/logout', { preHandler: requireAuth }, async (request, reply) => {
    await revokeAllRefreshTokens(request.userId!);
    return reply.send({ ok: true });
  });

  app.get('/v1/me', { preHandler: requireAuth }, async (request, reply) => {
    const user = await prisma.user.findUnique({
      where: { id: request.userId! },
      select: { id: true, email: true, name: true, createdAt: true },
    });
    if (!user) return reply.code(404).send({ error: 'not_found' });
    return reply.send(user);
  });

  /**
   * Hesap silme. App Store yönergeleri, hesap açılabilen her uygulamada
   * uygulama içinden kalıcı silmeyi zorunlu tutar (Guideline 5.1.1(v)).
   * Şema tümüyle `onDelete: Cascade` kurulduğu için kullanıcıyı silmek tüm
   * ders, ödeme ve ödev kayıtlarını da siler.
   */
  app.delete('/v1/account', { preHandler: requireAuth }, async (request, reply) => {
    const userId = request.userId!;
    await prisma.user.delete({ where: { id: userId } }).catch(() => undefined);
    await revokeAllRefreshTokens(userId);
    return reply.send({ ok: true });
  });
}
