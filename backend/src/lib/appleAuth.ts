import crypto from 'node:crypto';
import { createRemoteJWKSet, jwtVerify } from 'jose';
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
