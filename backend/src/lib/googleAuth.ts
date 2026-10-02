import { createRemoteJWKSet, jwtVerify } from 'jose';
import { config } from '../config.js';

/// Google'ın kimlik jetonu anahtarları; jose kendi içinde önbellekler.
const googleKeys = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'), {
  cacheMaxAge: 10 * 60 * 1000,
  cooldownDuration: 30 * 1000,
});

export interface GoogleIdentity {
  sub: string;
  email?: string;
  emailVerified: boolean;
  name?: string;
}

export function googleSignInConfigured(): boolean {
  return Boolean(config.GOOGLE_WEB_CLIENT_ID);
}

/**
 * Android'deki Google ile Giriş'in verdiği kimlik jetonunu doğrular.
 *
 * Jeton Google'ın anahtarıyla imzalı, alıcısı (aud) bizim Web istemci
 * kimliğimiz olmalı. `nonce` verilirse jetondakiyle aynı olmalı: cihazın aldığı
 * jetonun başkasınca yeniden kullanılmasını (replay) engeller.
 */
export async function verifyGoogleIdToken(idToken: string, nonce?: string): Promise<GoogleIdentity> {
  if (!config.GOOGLE_WEB_CLIENT_ID) throw new Error('Google ile giriş yapılandırılmadı.');

  const { payload } = await jwtVerify(idToken, googleKeys, {
    issuer: ['https://accounts.google.com', 'accounts.google.com'],
    audience: config.GOOGLE_WEB_CLIENT_ID,
  });

  if (typeof payload.sub !== 'string' || payload.sub.length === 0) {
    throw new Error('Kimlik jetonunda kullanıcı kimliği yok.');
  }
  if (nonce && payload.nonce !== nonce) {
    throw new Error('Nonce eşleşmedi.');
  }

  return {
    sub: payload.sub,
    email: typeof payload.email === 'string' ? payload.email : undefined,
    emailVerified: payload.email_verified === true || payload.email_verified === 'true',
    name: typeof payload.name === 'string' ? payload.name : undefined,
  };
}
