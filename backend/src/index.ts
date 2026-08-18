import Fastify from 'fastify';
import cors from '@fastify/cors';
import helmet from '@fastify/helmet';
import rateLimit from '@fastify/rate-limit';
import fastifyStatic from '@fastify/static';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { config } from './config.js';
import { prisma } from './lib/prisma.js';
import { redis } from './lib/redis.js';
import { authRoutes } from './routes/auth.js';
import { syncRoutes } from './routes/sync.js';
import { subscriptionRoutes } from './routes/subscription.js';
import { legalRoutes } from './routes/legal.js';
import { siteRoutes } from './routes/site.js';

const app = Fastify({
  logger: {
    level: config.NODE_ENV === 'development' ? 'debug' : 'info',
    // Kimlik jetonları ve imzalı işlemler loga düşmemeli.
    redact: ['req.headers.authorization', 'body.identityToken', 'body.signedTransaction'],
  },
  trustProxy: true,
  bodyLimit: 4 * 1024 * 1024,
});

// Fastify varsayılanı, "application/json" başlığı taşıyan boş gövdeli isteği
// 400 ile reddeder. DELETE /v1/account gövdesizdir ama birçok istemci başlığı
// yine de gönderir; bu uç App Store incelemesinde bizzat denendiği için
// boş gövde hatasız kabul edilir.
app.addContentTypeParser('application/json', { parseAs: 'string' }, (_request, body, done) => {
  const text = typeof body === 'string' ? body.trim() : '';
  if (text.length === 0) return done(null, undefined);
  try {
    done(null, JSON.parse(text));
  } catch {
    done(new Error('Geçersiz JSON gövdesi'), undefined);
  }
});

await app.register(helmet, { contentSecurityPolicy: false });

// İstemci yerel bir iOS uygulaması olduğu için tarayıcı kaynaklı istek
// beklenmez; CORS yalnızca yasal sayfaların açılabilmesi için serbest.
await app.register(cors, { origin: true });

await app.register(rateLimit, {
  max: 300,
  timeWindow: '1 minute',
  redis,
  keyGenerator: (request) => request.userId ?? request.ip,
});

await app.register(authRoutes);
await app.register(syncRoutes);
await app.register(subscriptionRoutes);
// Tanıtım sayfasının ekran görüntüleri
await app.register(fastifyStatic, {
  root: path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../public'),
  prefix: '/',
  cacheControl: true,
  maxAge: '7d',
});

await app.register(legalRoutes);
await app.register(siteRoutes);

app.get('/health', async () => {
  await prisma.$queryRaw`SELECT 1`;
  await redis.ping();
  return { ok: true, service: 'dersdefteri', time: new Date().toISOString() };
});

const shutdown = async (signal: string) => {
  app.log.info({ signal }, 'kapanıyor');
  await app.close();
  await prisma.$disconnect();
  redis.disconnect();
  process.exit(0);
};

process.on('SIGTERM', () => void shutdown('SIGTERM'));
process.on('SIGINT', () => void shutdown('SIGINT'));

try {
  await app.listen({ port: config.PORT, host: config.HOST });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
