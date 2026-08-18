import 'dotenv/config';
import { z } from 'zod';

const schema = z.object({
  NODE_ENV: z.string().default('production'),
  PORT: z.coerce.number().default(4001),
  HOST: z.string().default('0.0.0.0'),

  DATABASE_URL: z.string().min(1),
  REDIS_URL: z.string().min(1),

  /// Erişim ve yenileme jetonlarını imzalar. Değişirse tüm oturumlar düşer.
  JWT_SECRET: z.string().min(32, 'JWT_SECRET en az 32 karakter olmalı'),

  APPLE_BUNDLE_ID: z.string().min(1),

  /// App Store Server API kimlik bilgileri. Boş bırakılabilir: bu durumda
  /// abonelik yalnızca cihazın gönderdiği imzalı işlemle doğrulanır (bu tek
  /// başına kriptografik olarak yeterlidir), fakat sunucu Apple'a durum
  /// sorgusu atamaz ve iptal/yenileme yalnız webhook ile öğrenilir.
  APPLE_ISSUER_ID: z.string().optional(),
  APPLE_KEY_ID: z.string().optional(),
  APPLE_PRIVATE_KEY_PATH: z.string().optional(),
  APPSTORE_ENVIRONMENT: z.enum(['Production', 'Sandbox']).default('Production'),

  /// App Store Connect'te göstereceğin URL'ler bu sunucudan servis edilir.
  PUBLIC_BASE_URL: z.string().default('https://dersapi.adilemree.xyz'),
  SUPPORT_EMAIL: z.string().default('destek@adilemree.xyz'),
});

const parsed = schema.safeParse(process.env);

if (!parsed.success) {
  console.error('Yapılandırma hatalı:', parsed.error.flatten().fieldErrors);
  process.exit(1);
}

export const config = parsed.data;

/// Redis anahtarları bu önekle yazılır. Aynı Redis örneğini paylaşan diğer
/// uygulamalarla çakışmayı bu önek ve ayrı DB indeksi birlikte engeller.
export const REDIS_PREFIX = 'dd:';
