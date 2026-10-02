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

  /// Apple ile Giriş REST API anahtarı (kod takası ve hesap silinirken iptal).
  /// Satın alma anahtarından ayrıdır: Apple Developer → Keys altında
  /// "Sign in with Apple" işaretlenerek üretilir. Boş bırakılırsa giriş yine
  /// çalışır, yalnızca hesap silinirken Apple tarafındaki bağ koparılamaz.
  APPLE_TEAM_ID: z.string().optional(),
  APPLE_SIGNIN_KEY_ID: z.string().optional(),
  APPLE_SIGNIN_PRIVATE_KEY_PATH: z.string().optional(),

  /// Android: Google ile Giriş. Google Cloud'daki "Web uygulaması" türündeki
  /// OAuth istemci kimliği; Android uygulaması kimlik jetonunu bu kimlik için
  /// ister. Boş bırakılırsa Google ile giriş kapalıdır.
  GOOGLE_WEB_CLIENT_ID: z.string().optional(),

  /// Android paket adı; Play satın almaları bu paket için doğrulanır.
  GOOGLE_PLAY_PACKAGE: z.string().default('xyz.adilemree.dersdefteri'),

  /// Google Play Developer API hizmet hesabı anahtarı (JSON dosyası). Boş
  /// bırakılırsa Android satın almaları sunucuda doğrulanamaz ve Android'de
  /// eşitleme Pro olarak tanınmaz.
  GOOGLE_PLAY_SERVICE_ACCOUNT_PATH: z.string().optional(),

  /// Google Play gerçek zamanlı bildirimleri (Pub/Sub push) için adreste
  /// taşınan gizli değer. Boşsa bildirim ucu kapalıdır; iptal ve iadeler
  /// sunucunun periyodik Play sorgusuyla öğrenilir.
  GOOGLE_RTDN_TOKEN: z.string().optional(),

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
