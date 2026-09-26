import crypto from 'node:crypto';
import fs from 'node:fs';
import { createRemoteJWKSet, importPKCS8, jwtVerify, SignJWT } from 'jose';
import { config } from '../config.js';

const APPLE_ISSUER = 'https://appleid.apple.com';

/// jose anahtar kümesini kendi içinde önbellekler ve döndürülen anahtarları
/// arka planda tazeler; her girişte Apple'a istek atılmaz.
const appleKeys = createRemoteJWKSet(new URL(`${APPLE_ISSUER}/auth/keys`), {
  cacheMaxAge: 10 * 60 * 1000,
  cooldownDuration: 30 * 1000,
});

export interface AppleIdentity {
  sub: string;
  email?: string;
  emailVerified: boolean;
  /// Apple e-postayı yalnızca ilk girişte döndürür; sonraki girişlerde boş gelir.
  isPrivateEmail: boolean;
}

/**
 * Apple'ın imzaladığı kimlik jetonunu doğrular.
 *
 * `rawNonce` verilirse jetonun `nonce` iddiasıyla karşılaştırılır. Bu, cihazın
 * aldığı jetonun başkasınca yeniden kullanılmasını (replay) engeller; istemci
 * Apple'a nonce'un SHA-256'sını gönderdiği için burada aynı özet hesaplanır.
 */
export async function verifyAppleIdentityToken(
  identityToken: string,
  rawNonce?: string,
): Promise<AppleIdentity> {
  const { payload } = await jwtVerify(identityToken, appleKeys, {
    issuer: APPLE_ISSUER,
    audience: config.APPLE_BUNDLE_ID,
  });

  if (typeof payload.sub !== 'string' || payload.sub.length === 0) {
    throw new Error('Kimlik jetonunda kullanıcı kimliği yok.');
  }

  if (rawNonce) {
    const expected = crypto.createHash('sha256').update(rawNonce).digest('hex');
    if (payload.nonce !== expected) {
      throw new Error('Nonce eşleşmedi.');
    }
  }

  const emailVerifiedClaim = payload.email_verified;
  const privateEmailClaim = payload.is_private_email;

  return {
    sub: payload.sub,
    email: typeof payload.email === 'string' ? payload.email : undefined,
    // Apple bu iki alanı bazı sürümlerde metin ("true") olarak gönderir.
    emailVerified: emailVerifiedClaim === true || emailVerifiedClaim === 'true',
    isPrivateEmail: privateEmailClaim === true || privateEmailClaim === 'true',
  };
}

// MARK: - Apple ile Giriş REST API

/**
 * Hesap silinirken Apple girişinin de iptal edilmesi gerekir (App Store
 * yönergesi 5.1.1(v)); aksi halde kullanıcı iPhone Ayarlar'ında uygulamayı
 * hâlâ bağlı görür. İptal için Apple'ın verdiği yenileme jetonu lazım; o da
 * yalnızca girişteki tek kullanımlık yetki kodu takas edilerek alınır.
 */
function signInKeyPath(): string | null {
  const { APPLE_TEAM_ID, APPLE_SIGNIN_KEY_ID, APPLE_SIGNIN_PRIVATE_KEY_PATH } = config;
  if (!APPLE_TEAM_ID || !APPLE_SIGNIN_KEY_ID || !APPLE_SIGNIN_PRIVATE_KEY_PATH) return null;
  if (!fs.existsSync(APPLE_SIGNIN_PRIVATE_KEY_PATH)) {
    console.warn('[apple] Sign in with Apple anahtarı bulunamadı:', APPLE_SIGNIN_PRIVATE_KEY_PATH);
    return null;
  }
  return APPLE_SIGNIN_PRIVATE_KEY_PATH;
}

let signingKey: ReturnType<typeof importPKCS8> | null = null;

/// Apple'ın istemci sırrı: anahtarla imzalanmış kısa ömürlü bir JWT.
async function clientSecret(keyPath: string): Promise<string> {
  signingKey ??= importPKCS8(fs.readFileSync(keyPath, 'utf8'), 'ES256');
  return new SignJWT({})
    .setProtectedHeader({ alg: 'ES256', kid: config.APPLE_SIGNIN_KEY_ID! })
    .setIssuer(config.APPLE_TEAM_ID!)
    .setSubject(config.APPLE_BUNDLE_ID)
    .setAudience(APPLE_ISSUER)
    .setIssuedAt()
    .setExpirationTime('5m')
    .sign(await signingKey);
}

async function postForm(endpoint: string, fields: Record<string, string>): Promise<Response> {
  return fetch(`${APPLE_ISSUER}${endpoint}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields),
    signal: AbortSignal.timeout(10_000),
  });
}

/// Yetki kodunu Apple'ın yenileme jetonuyla takas eder. Anahtar
/// yapılandırılmamışsa `null` döner.
export async function exchangeAuthorizationCode(code: string): Promise<string | null> {
  const keyPath = signInKeyPath();
  if (!keyPath) return null;

  const res = await postForm('/auth/token', {
    client_id: config.APPLE_BUNDLE_ID,
    client_secret: await clientSecret(keyPath),
    code,
    grant_type: 'authorization_code',
  });
  if (!res.ok) {
    throw new Error(`Apple kod takası başarısız: ${res.status} ${await res.text()}`);
  }
  const body = (await res.json()) as { refresh_token?: string };
  return body.refresh_token ?? null;
}

/// Kullanıcının Apple girişini bu uygulama için iptal eder.
export async function revokeAppleToken(refreshToken: string): Promise<void> {
  const keyPath = signInKeyPath();
  if (!keyPath) return;

  const res = await postForm('/auth/revoke', {
    client_id: config.APPLE_BUNDLE_ID,
    client_secret: await clientSecret(keyPath),
    token: refreshToken,
    token_type_hint: 'refresh_token',
  });
  if (!res.ok) {
    throw new Error(`Apple jetonu iptal edilemedi: ${res.status} ${await res.text()}`);
  }
}
