import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { prisma } from '../lib/prisma.js';
import { redis } from '../lib/redis.js';
import { requireAuth } from '../lib/auth.js';
import {
  statusFromNotification,
  verifyNotification,
  verifyRenewalInfo,
  verifyTransaction,
} from '../lib/appstore.js';

const ENTITLEMENT_TTL_SECONDS = 300;

export interface Entitlement {
  isPro: boolean;
  productId: string | null;
  expiresAt: string | null;
  status: string;
}

const FREE: Entitlement = { isPro: false, productId: null, expiresAt: null, status: 'none' };

function entitlementFrom(sub: {
  status: string;
  productId: string;
  expiresAt: Date | null;
  revokedAt: Date | null;
} | null): Entitlement {
  if (!sub || sub.revokedAt) return FREE;
  const notExpired = !sub.expiresAt || sub.expiresAt.getTime() > Date.now();
  // Ek süre, ödeme yeniden denenirken erişimin kesilmemesi için aktif sayılır.
  const isPro = (sub.status === 'active' || sub.status === 'grace') && notExpired;
  return {
    isPro,
    productId: sub.productId,
    expiresAt: sub.expiresAt?.toISOString() ?? null,
    status: sub.status,
  };
}

export async function readEntitlement(userId: string): Promise<Entitlement> {
  const cached = await redis.get(`ent:${userId}`);
  if (cached) return JSON.parse(cached) as Entitlement;

  const sub = await prisma.subscription.findUnique({ where: { userId } });
  const entitlement = entitlementFrom(sub);
  await redis.set(`ent:${userId}`, JSON.stringify(entitlement), 'EX', ENTITLEMENT_TTL_SECONDS);
  return entitlement;
}

async function invalidateEntitlement(userId: string): Promise<void> {
  await redis.del(`ent:${userId}`);
}

export async function subscriptionRoutes(app: FastifyInstance) {
  app.get('/v1/subscription', { preHandler: requireAuth }, async (request, reply) => {
    return reply.send(await readEntitlement(request.userId!));
  });

  /**
   * Cihazdan gelen imzalı işlemi doğrular ve aboneliği kullanıcıya bağlar.
   *
   * İmza Apple'ın kök sertifikasına kadar doğrulandığı için bu tek başına
   * yetkilendirme kaynağı olarak güvenilirdir; sunucunun ayrıca Apple'a
   * sorması gerekmez. Yenileme ve iptaller webhook ile gelir.
   */
  app.post('/v1/subscription/verify', { preHandler: requireAuth }, async (request, reply) => {
    const parsed = z
      .object({ signedTransaction: z.string().min(1) })
      .safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_body', message: 'İşlem verisi eksik.' });
    }

    let tx;
    try {
      tx = await verifyTransaction(parsed.data.signedTransaction);
    } catch (err) {
      request.log.warn({ err }, 'imzalı işlem doğrulanamadı');
      return reply
        .code(400)
        .send({ error: 'invalid_transaction', message: 'Satın alma doğrulanamadı.' });
    }

    const originalTransactionId = tx.originalTransactionId;
    const productId = tx.productId;
    if (!originalTransactionId || !productId) {
      return reply.code(400).send({ error: 'invalid_transaction', message: 'İşlem eksik.' });
    }

    const userId = request.userId!;
    const expiresAt = tx.expiresDate ? new Date(tx.expiresDate) : null;
    const revokedAt = tx.revocationDate ? new Date(tx.revocationDate) : null;

    // Aynı Apple aboneliği başka bir hesaba bağlıysa devret: kullanıcı hesabını
    // silip yeniden açtığında ya da Apple kimliğini değiştirdiğinde satın
    // aldığı abonelik kaybolmamalı.
    const existing = await prisma.subscription.findUnique({ where: { originalTransactionId } });
    if (existing && existing.userId !== userId) {
      await prisma.subscription.delete({ where: { id: existing.id } });
      await invalidateEntitlement(existing.userId);
    }

    const record = {
      productId,
      status: revokedAt ? 'revoked' : 'active',
      environment: tx.environment ?? 'Production',
      expiresAt,
      revokedAt,
    };

    const saved = await prisma.subscription.upsert({
      where: { userId },
      create: { userId, originalTransactionId, ...record },
      update: { originalTransactionId, ...record },
    });

    await invalidateEntitlement(userId);
    return reply.send(entitlementFrom(saved));
  });

  /**
   * App Store Server Notifications V2 uç noktası.
   *
   * Kimlik doğrulaması imzanın kendisidir: gövde Apple tarafından imzalanmış
   * bir JWS'tir ve kök sertifikaya kadar doğrulanır. Bu yüzden uç açık
   * bırakılır, ayrıca bir jeton beklenmez.
   */
  app.post('/v1/webhooks/appstore', async (request, reply) => {
    const parsed = z.object({ signedPayload: z.string().min(1) }).safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: 'invalid_body' });

    let payload;
    try {
      payload = await verifyNotification(parsed.data.signedPayload);
    } catch (err) {
      request.log.warn({ err }, 'app store bildirimi doğrulanamadı');
      return reply.code(400).send({ error: 'invalid_signature' });
    }

    const info = payload.data;
    if (!info?.signedTransactionInfo) {
      // TEST bildirimi ve abonelik dışı olaylar burada biter.
      return reply.send({ ok: true });
    }

    const tx = await verifyTransaction(info.signedTransactionInfo);
    const originalTransactionId = tx.originalTransactionId;
    if (!originalTransactionId) return reply.send({ ok: true });

    const subscription = await prisma.subscription.findUnique({
      where: { originalTransactionId },
    });
    if (!subscription) {
      // Henüz hiçbir cihaz bu aboneliği hesaba bağlamamış. Cihaz açıldığında
      // /v1/subscription/verify ile bağlayacağı için burada yapacak bir şey yok.
      return reply.send({ ok: true });
    }

    const mapped = statusFromNotification(payload.notificationType ?? '', payload.subtype);
    let autoRenew = subscription.autoRenew;
    if (info.signedRenewalInfo) {
      try {
        const renewal = await verifyRenewalInfo(info.signedRenewalInfo);
        autoRenew = renewal.autoRenewStatus === 1;
      } catch {
        // Yenileme bilgisi okunamazsa mevcut değer korunur.
      }
    }

    await prisma.subscription.update({
      where: { id: subscription.id },
      data: {
        productId: tx.productId ?? subscription.productId,
        status: mapped ?? subscription.status,
        expiresAt: tx.expiresDate ? new Date(tx.expiresDate) : subscription.expiresAt,
        revokedAt: tx.revocationDate ? new Date(tx.revocationDate) : null,
        autoRenew,
      },
    });

    await invalidateEntitlement(subscription.userId);
    request.log.info(
      { type: payload.notificationType, subtype: payload.subtype },
      'abonelik güncellendi',
    );
    return reply.send({ ok: true });
  });
}
