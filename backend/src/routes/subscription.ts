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
import { config } from '../config.js';
import {
  googlePlayConfigured,
  PlayPurchaseNotFound,
  verifyPlayPurchase,
} from '../lib/googleplay.js';

const ENTITLEMENT_TTL_SECONDS = 300;

/// Tek seferlik satın alma; süresi yoktur (`expiresAt` boş kalır).
const LIFETIME_PRODUCT_ID = 'dersdefteri.omurboyu';

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

  let sub = await prisma.subscription.findUnique({ where: { userId } });
  if (sub && sub.store === 'google_play') sub = await refreshGooglePurchase(sub);
  const entitlement = entitlementFrom(sub);
  await redis.set(`ent:${userId}`, JSON.stringify(entitlement), 'EX', ENTITLEMENT_TTL_SECONDS);
  return entitlement;
}

async function invalidateEntitlement(userId: string): Promise<void> {
  await redis.del(`ent:${userId}`);
}

type SubscriptionRow = NonNullable<Awaited<ReturnType<typeof prisma.subscription.findUnique>>>;

/**
 * Google Play'in App Store'daki gibi imzalı bildirimi her zaman gelmeyebilir
 * (gerçek zamanlı bildirimler ayrıca kurulur). Bu yüzden Android kaydı
 * okunurken gerekiyorsa Play'e yeniden sorulur: süresi dolmuş görünen
 * abonelik (yenilenmiş olabilir) saatte bir, aktif olan (iade ya da iptal
 * için) günde bir. Play'e ulaşılamazsa son bilinen durum geçerli kalır.
 */
async function refreshGooglePurchase(sub: SubscriptionRow): Promise<SubscriptionRow> {
  if (!googlePlayConfigured() || sub.revokedAt) return sub;
  const age = Date.now() - (sub.lastCheckedAt?.getTime() ?? 0);
  const due = entitlementFrom(sub).isPro ? age > 24 * 3600_000 : age > 3600_000;
  if (!due) return sub;
  try {
    const state = await verifyPlayPurchase(sub.productId, sub.originalTransactionId.replace(/^gp:/, ''));
    return await prisma.subscription.update({
      where: { id: sub.id },
      data: {
        productId: state.productId,
        status: state.status,
        expiresAt: state.expiresAt,
        revokedAt: state.revokedAt,
        autoRenew: state.autoRenew,
        lastCheckedAt: new Date(),
      },
    });
  } catch (err) {
    if (err instanceof PlayPurchaseNotFound) {
      return await prisma.subscription.update({
        where: { id: sub.id },
        data: { status: 'revoked', revokedAt: new Date(), lastCheckedAt: new Date() },
      });
    }
    // Geçici hata: bir sonraki denemeye kadar Play'e yük bindirilmez.
    return await prisma.subscription.update({ where: { id: sub.id }, data: { lastCheckedAt: new Date() } });
  }
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

    // Girişliyken yapılan satın alma hesap kimliğiyle damgalıdır. Damga hâlâ
    // var olan başka bir hesabı gösteriyorsa abonelik o hesabındır; ele geçen
    // bir işlem kaydıyla başka hesaba taşınamaz. Damgasız işlemler (hesapsız
    // satın alma) ve silinmiş hesaba ait damgalar aşağıdaki devir kuralına düşer.
    const owner = tx.appAccountToken?.toLowerCase();
    if (owner && owner !== userId.toLowerCase()) {
      const ownerExists = await prisma.user.findUnique({ where: { id: owner }, select: { id: true } });
      if (ownerExists) {
        return reply
          .code(409)
          .send({ error: 'subscription_owned', message: 'Bu abonelik başka bir hesaba bağlı.' });
      }
    }

    // Kullanıcı başına tek kayıt tutulur. Ömür boyu satın alan birinin eski
    // aboneliği (ya da bunu bilmeyen eski bir uygulama sürümü) kaydı ezerse,
    // abonelik bittiğinde Pro yanlışlıkla kapanırdı.
    const current = await prisma.subscription.findUnique({ where: { userId } });
    if (
      current &&
      current.productId === LIFETIME_PRODUCT_ID &&
      !current.revokedAt &&
      productId !== LIFETIME_PRODUCT_ID
    ) {
      return reply.send(entitlementFrom(current));
    }

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
      store: 'app_store',
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
   * Android: cihazdaki Google Play satın almasını doğrular ve hesaba bağlar.
   *
   * Satın alma jetonu Google Play Developer API'ye sorulur; durum ve süre
   * yalnızca Google'ın yanıtından alınır. Kurallar App Store ile aynıdır:
   * başka bir (var olan) hesaba damgalı satın alma taşınamaz, ömür boyu kaydı
   * abonelikle ezilmez, silinmiş hesabın satın alması yeni hesaba devredilir.
   */
  app.post('/v1/subscription/verify-google', { preHandler: requireAuth }, async (request, reply) => {
    const parsed = z
      .object({ productId: z.string().min(1).max(200), purchaseToken: z.string().min(1).max(4096) })
      .safeParse(request.body);
    if (!parsed.success) {
      return reply.code(400).send({ error: 'invalid_body', message: 'Satın alma bilgisi eksik.' });
    }
    if (!googlePlayConfigured()) {
      return reply.code(503).send({ error: 'not_configured', message: 'Google Play doğrulaması henüz yapılandırılmadı.' });
    }

    const { productId, purchaseToken } = parsed.data;
    let state;
    try {
      state = await verifyPlayPurchase(productId, purchaseToken);
    } catch (err) {
      if (err instanceof PlayPurchaseNotFound) {
        return reply.code(400).send({ error: 'invalid_transaction', message: 'Satın alma doğrulanamadı.' });
      }
      request.log.warn({ err }, 'google play satın alması doğrulanamadı');
      return reply.code(502).send({ error: 'play_unavailable', message: 'Google Play şu an yanıt vermiyor. Birazdan tekrar denenecek.' });
    }

    const userId = request.userId!;
    const owner = state.accountId?.toLowerCase();
    if (owner && owner !== userId.toLowerCase()) {
      const ownerExists = await prisma.user.findUnique({ where: { id: owner }, select: { id: true } });
      if (ownerExists) {
        return reply
          .code(409)
          .send({ error: 'subscription_owned', message: 'Bu abonelik başka bir hesaba bağlı.' });
      }
    }

    const current = await prisma.subscription.findUnique({ where: { userId } });
    if (
      current &&
      current.productId === LIFETIME_PRODUCT_ID &&
      !current.revokedAt &&
      state.productId !== LIFETIME_PRODUCT_ID
    ) {
      return reply.send(entitlementFrom(current));
    }

    const originalTransactionId = `gp:${purchaseToken}`;
    const existing = await prisma.subscription.findUnique({ where: { originalTransactionId } });
    if (existing && existing.userId !== userId) {
      await prisma.subscription.delete({ where: { id: existing.id } });
      await invalidateEntitlement(existing.userId);
    }

    const record = {
      productId: state.productId,
      status: state.status,
      environment: state.test ? 'GooglePlayTest' : 'GooglePlay',
      expiresAt: state.expiresAt,
      revokedAt: state.revokedAt,
      autoRenew: state.autoRenew,
      store: 'google_play',
      lastCheckedAt: new Date(),
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
   * Google Play gerçek zamanlı geliştirici bildirimleri (Pub/Sub push).
   *
   * Bildirim yalnızca hangi satın almanın değiştiğini söyler; asıl durum
   * Play'e yeniden sorulur. Adresteki gizli değer eşleşmezse istek reddedilir.
   */
  app.post('/v1/webhooks/googleplay', async (request, reply) => {
    const token = (request.query as { token?: string } | undefined)?.token;
    if (!config.GOOGLE_RTDN_TOKEN || token !== config.GOOGLE_RTDN_TOKEN) {
      return reply.code(401).send({ error: 'unauthorized' });
    }
    const body = z.object({ message: z.object({ data: z.string() }) }).safeParse(request.body);
    if (!body.success) return reply.code(400).send({ error: 'invalid_body' });

    let note: {
      subscriptionNotification?: { purchaseToken?: string; notificationType?: number };
      oneTimeProductNotification?: { purchaseToken?: string };
      voidedPurchaseNotification?: { purchaseToken?: string };
      testNotification?: object;
    };
    try {
      note = JSON.parse(Buffer.from(body.data.message.data, 'base64').toString('utf8'));
    } catch {
      // Çözülemeyen bildirim yeniden gönderilmesin diye yine onaylanır.
      return reply.send({ ok: true });
    }

    const purchaseToken =
      note.subscriptionNotification?.purchaseToken ??
      note.oneTimeProductNotification?.purchaseToken ??
      note.voidedPurchaseNotification?.purchaseToken;
    if (!purchaseToken) return reply.send({ ok: true });

    const sub = await prisma.subscription.findUnique({ where: { originalTransactionId: `gp:${purchaseToken}` } });
    // Henüz hiçbir hesaba bağlanmamış satın alma; cihaz giriş yapınca bağlar.
    if (!sub) return reply.send({ ok: true });

    if (note.voidedPurchaseNotification) {
      await prisma.subscription.update({
        where: { id: sub.id },
        data: { status: 'revoked', revokedAt: new Date(), lastCheckedAt: new Date() },
      });
    } else {
      // Son sorgu zamanı sıfırlanır; böylece kayıt hemen Play'e sorulur.
      await prisma.subscription.update({ where: { id: sub.id }, data: { lastCheckedAt: null } });
      const fresh = await prisma.subscription.findUnique({ where: { id: sub.id } });
      if (fresh) await refreshGooglePurchase(fresh);
    }
    await invalidateEntitlement(sub.userId);
    request.log.info({ type: note.subscriptionNotification?.notificationType ?? 'other' }, 'google play bildirimi işlendi');
    return reply.send({ ok: true });
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
