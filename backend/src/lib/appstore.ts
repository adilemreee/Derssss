import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  AppStoreServerAPIClient,
  Environment,
  SignedDataVerifier,
  type JWSTransactionDecodedPayload,
  type JWSRenewalInfoDecodedPayload,
  type ResponseBodyV2DecodedPayload,
} from '@apple/app-store-server-library';
import { config } from '../config.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const certsDir = path.resolve(here, '../../certs');

/// Apple'ın kök sertifikaları. İmzalı işlemin gerçekten Apple'dan geldiği
/// yalnızca bu zincirle doğrulanabilir; eksikse doğrulama hiç yapılmamalıdır.
function loadAppleRootCerts(): Buffer[] {
  if (!fs.existsSync(certsDir)) return [];
  return fs
    .readdirSync(certsDir)
    .filter((f) => f.endsWith('.cer') || f.endsWith('.der'))
    .map((f) => fs.readFileSync(path.join(certsDir, f)));
}

const environment =
  config.APPSTORE_ENVIRONMENT === 'Sandbox' ? Environment.SANDBOX : Environment.PRODUCTION;

const verifierCache = new Map<Environment, SignedDataVerifier>();

function buildVerifier(env: Environment): SignedDataVerifier {
  const cached = verifierCache.get(env);
  if (cached) return cached;

  const roots = loadAppleRootCerts();
  if (roots.length === 0) {
    throw new Error('Apple kök sertifikaları bulunamadı (certs/ boş).');
  }

  const appAppleId = process.env.APPLE_APP_APPLE_ID
    ? Number(process.env.APPLE_APP_APPLE_ID)
    : undefined;

  // Üretim ortamında Apple kütüphanesi uygulamanın sayısal App Store kimliğini
  // zorunlu tutar; App Store Connect'te uygulama kaydı açılınca verilir.
  const verifier = new SignedDataVerifier(
    roots,
    true, // çevrimiçi iptal kontrolü
    env,
    config.APPLE_BUNDLE_ID,
    env === Environment.PRODUCTION ? appAppleId : undefined,
  );
  verifierCache.set(env, verifier);
  return verifier;
}

/**
 * Yapılandırılan ortamı önce dener, olmazsa diğerine düşer.
 *
 * Aynı uygulama hem App Store'dan (Production) hem TestFlight/Sandbox'tan
 * imzalı işlem alır ve iki ortamın imzası birbirini doğrulamaz. Apple'ın
 * önerdiği yol, ortamı sabitlemek yerine ikisini sırayla denemektir; aksi
 * halde TestFlight kullanıcıları abonelik doğrulatamaz.
 */
async function withEitherEnvironment<T>(
  run: (verifier: SignedDataVerifier) => Promise<T>,
): Promise<T> {
  const primary = environment;
  const fallback =
    primary === Environment.PRODUCTION ? Environment.SANDBOX : Environment.PRODUCTION;

  try {
    return await run(buildVerifier(primary));
  } catch (primaryError) {
    try {
      return await run(buildVerifier(fallback));
    } catch {
      // İki ortamda da doğrulanamadıysa asıl ortamın hatası daha bilgilendirici.
      throw primaryError;
    }
  }
}

let apiClient: AppStoreServerAPIClient | null = null;

/// App Store Server API isteğe bağlıdır: yapılandırılmadığında abonelik yine
/// imzalı işlemden ve webhook'tan yürür, yalnızca durum sorgusu yapılamaz.
export function getApiClient(): AppStoreServerAPIClient | null {
  if (apiClient) return apiClient;
  const { APPLE_ISSUER_ID, APPLE_KEY_ID, APPLE_PRIVATE_KEY_PATH } = config;
  if (!APPLE_ISSUER_ID || !APPLE_KEY_ID || !APPLE_PRIVATE_KEY_PATH) return null;
  if (!fs.existsSync(APPLE_PRIVATE_KEY_PATH)) {
    console.warn('[appstore] .p8 anahtarı bulunamadı:', APPLE_PRIVATE_KEY_PATH);
    return null;
  }
  const signingKey = fs.readFileSync(APPLE_PRIVATE_KEY_PATH, 'utf8');
  apiClient = new AppStoreServerAPIClient(
    signingKey,
    APPLE_KEY_ID,
    APPLE_ISSUER_ID,
    config.APPLE_BUNDLE_ID,
    environment,
  );
  return apiClient;
}

export async function verifyTransaction(
  signedTransaction: string,
): Promise<JWSTransactionDecodedPayload> {
  return withEitherEnvironment((v) => v.verifyAndDecodeTransaction(signedTransaction));
}

export async function verifyNotification(
  signedPayload: string,
): Promise<ResponseBodyV2DecodedPayload> {
  return withEitherEnvironment((v) => v.verifyAndDecodeNotification(signedPayload));
}

export async function verifyRenewalInfo(
  signedRenewalInfo: string,
): Promise<JWSRenewalInfoDecodedPayload> {
  return withEitherEnvironment((v) => v.verifyAndDecodeRenewalInfo(signedRenewalInfo));
}

export type EntitlementStatus = 'active' | 'grace' | 'expired' | 'revoked';

/**
 * App Store bildirim tipini abonelik durumuna çevirir.
 *
 * Yalnızca `EXPIRED`, `REVOKE` ve `REFUND` erişimi gerçekten kapatır. Ödeme
 * alınamadığında (`DID_FAIL_TO_RENEW`) Apple bir ek süre tanır; kullanıcı bu
 * süre boyunca ödeme yapıp devam edebileceği için erişim kesilmez.
 */
export function statusFromNotification(
  notificationType: string,
  subtype: string | undefined,
): EntitlementStatus | null {
  switch (notificationType) {
    case 'SUBSCRIBED':
    case 'DID_RENEW':
    case 'OFFER_REDEEMED':
      return 'active';
    case 'DID_CHANGE_RENEWAL_STATUS':
      // Otomatik yenileme kapatılmış olsa da mevcut dönem sonuna kadar aktiftir.
      return 'active';
    case 'DID_FAIL_TO_RENEW':
      // Ek süre de, ödeme yeniden denemesi de kullanıcının hâlâ ödeme yapıp
      // devam edebileceği durumlardır; ikisinde de erişim korunur.
      return 'grace';
    case 'GRACE_PERIOD_EXPIRED':
    case 'EXPIRED':
      return 'expired';
    case 'REFUND':
    case 'REVOKE':
      return 'revoked';
    default:
      return null;
  }
}
