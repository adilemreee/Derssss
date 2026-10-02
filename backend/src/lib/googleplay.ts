import fs from 'node:fs';
import { importPKCS8, SignJWT } from 'jose';
import { config } from '../config.js';

// Google Play satın alma doğrulaması (Android). Cihazın gönderdiği satın alma
// jetonu Google Play Developer API'ye sorulur; yanıtı yalnızca Google verir,
// istemcinin söylediğine güvenilmez.

export const LIFETIME_PRODUCT_ID = 'dersdefteri.omurboyu';
export const SUBSCRIPTION_PRODUCT_IDS = ['dersdefteri.abonelik.aylik', 'dersdefteri.abonelik.yillik'];

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

let account: ServiceAccount | null | undefined;
let cachedToken: { value: string; expiresAt: number } | null = null;

function serviceAccount(): ServiceAccount | null {
  if (account !== undefined) return account;
  const path = config.GOOGLE_PLAY_SERVICE_ACCOUNT_PATH;
  if (!path || !fs.existsSync(path)) {
    if (path) console.warn('[googleplay] hizmet hesabı dosyası bulunamadı:', path);
    account = null;
    return account;
  }
  try {
    const parsed = JSON.parse(fs.readFileSync(path, 'utf8')) as ServiceAccount;
    account = parsed.client_email && parsed.private_key ? parsed : null;
  } catch {
    console.warn('[googleplay] hizmet hesabı dosyası okunamadı');
    account = null;
  }
  return account;
}

export function googlePlayConfigured(): boolean {
  return serviceAccount() !== null;
}

/// Play'de bulunmayan ya da geçersiz satın alma jetonu.
export class PlayPurchaseNotFound extends Error {}

/// Hizmet hesabıyla kısa ömürlü erişim jetonu alır (yaklaşık bir saat geçerli).
async function accessToken(): Promise<string> {
  if (cachedToken && cachedToken.expiresAt - 60_000 > Date.now()) return cachedToken.value;
  const sa = serviceAccount();
  if (!sa) throw new Error('Google Play hizmet hesabı yapılandırılmadı.');

  const tokenUri = sa.token_uri ?? 'https://oauth2.googleapis.com/token';
  const key = await importPKCS8(sa.private_key, 'RS256');
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: 'https://www.googleapis.com/auth/androidpublisher' })
    .setProtectedHeader({ alg: 'RS256', typ: 'JWT' })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(key);

  const res = await fetch(tokenUri, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!res.ok) throw new Error(`Google erişim jetonu alınamadı (${res.status})`);
  const json = (await res.json()) as { access_token: string; expires_in: number };
  cachedToken = { value: json.access_token, expiresAt: Date.now() + json.expires_in * 1000 };
  return json.access_token;
}

async function api<T>(path: string): Promise<T> {
  const token = await accessToken();
  const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${encodeURIComponent(config.GOOGLE_PLAY_PACKAGE)}/${path}`;
  const res = await fetch(url, { headers: { Authorization: `Bearer ${token}` } });
  if (res.status === 400 || res.status === 404 || res.status === 410) {
    throw new PlayPurchaseNotFound(`Play satın alması bulunamadı (${res.status})`);
  }
  if (!res.ok) throw new Error(`Google Play API hatası (${res.status})`);
  return (await res.json()) as T;
}

interface ProductPurchase {
  /// 0 = satın alındı, 1 = iptal edildi (iade), 2 = beklemede
  purchaseState?: number;
  /// 0 = test satın alması
  purchaseType?: number;
  obfuscatedExternalAccountId?: string;
}

interface SubscriptionPurchaseV2 {
  subscriptionState?: string;
  testPurchase?: object;
  lineItems?: {
    productId: string;
    expiryTime?: string;
    autoRenewingPlan?: { autoRenewEnabled?: boolean };
  }[];
  externalAccountIdentifiers?: { obfuscatedExternalAccountId?: string };
}

export interface PlayPurchaseState {
  productId: string;
  status: 'active' | 'grace' | 'expired' | 'revoked';
  expiresAt: Date | null;
  revokedAt: Date | null;
  autoRenew: boolean;
  /// Satın almaya damgalanan hesap kimliği (giriş yapılmışsa)
  accountId?: string;
  test: boolean;
}

/// Satın alma jetonunu Play'e sorar ve sunucunun anlayacağı duruma çevirir.
export async function verifyPlayPurchase(productId: string, purchaseToken: string): Promise<PlayPurchaseState> {
  const token = encodeURIComponent(purchaseToken);

  if (productId === LIFETIME_PRODUCT_ID) {
    const p = await api<ProductPurchase>(`purchases/products/${encodeURIComponent(productId)}/tokens/${token}`);
    const status = p.purchaseState === 0 ? 'active' : p.purchaseState === 1 ? 'revoked' : 'expired';
    return {
      productId,
      status,
      expiresAt: null,
      revokedAt: status === 'revoked' ? new Date() : null,
      autoRenew: false,
      accountId: p.obfuscatedExternalAccountId,
      test: p.purchaseType === 0,
    };
  }

  const s = await api<SubscriptionPurchaseV2>(`purchases/subscriptionsv2/tokens/${token}`);
  const line = s.lineItems?.find((l) => SUBSCRIPTION_PRODUCT_IDS.includes(l.productId)) ?? s.lineItems?.[0];
  let status: PlayPurchaseState['status'];
  switch (s.subscriptionState) {
    // İptal edilmiş abonelik (yenileme kapalı) mevcut dönemin sonuna kadar geçerlidir.
    case 'SUBSCRIPTION_STATE_ACTIVE':
    case 'SUBSCRIPTION_STATE_CANCELED':
      status = 'active';
      break;
    // Ödeme alınamadı ama Play ek süre tanıdı; erişim kesilmez.
    case 'SUBSCRIPTION_STATE_IN_GRACE_PERIOD':
      status = 'grace';
      break;
    default:
      // ON_HOLD, PAUSED, EXPIRED, PENDING ve bilinmeyenler: erişim yok.
      status = 'expired';
  }
  return {
    productId: line?.productId ?? productId,
    status,
    expiresAt: line?.expiryTime ? new Date(line.expiryTime) : null,
    revokedAt: null,
    autoRenew: line?.autoRenewingPlan?.autoRenewEnabled ?? false,
    accountId: s.externalAccountIdentifiers?.obfuscatedExternalAccountId,
    test: Boolean(s.testPurchase),
  };
}
