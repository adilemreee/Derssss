#!/usr/bin/env bash
#
# App Store In-App Purchase anahtarını sunucuya kurar.
#
# Kullanım:
#   ./p8-yukle.sh ~/Downloads/SubscriptionKey_ABC1234567.p8 <ISSUER_ID>
#
# Key ID dosya adından okunur, Issuer ID'yi App Store Connect'teki
# In-App Purchase sayfasından kopyalayıp ikinci argüman olarak ver.

set -euo pipefail

P8="${1:-}"
ISSUER="${2:-}"
SSH_KEY="$HOME/.ssh/id_ed25519_sevgili"
HOST="root@92.5.38.182"
DIR="/opt/dersdefteri"

if [[ -z "$P8" || -z "$ISSUER" ]]; then
  echo "Kullanım: $0 <SubscriptionKey_XXXX.p8 yolu> <ISSUER_ID>" >&2
  exit 1
fi

if [[ ! -f "$P8" ]]; then
  echo "Dosya bulunamadı: $P8" >&2
  exit 1
fi

# Key ID dosya adında gömülü: SubscriptionKey_ABC1234567.p8 -> ABC1234567
KEY_ID="$(basename "$P8" .p8)"
KEY_ID="${KEY_ID##*_}"

if [[ ! "$KEY_ID" =~ ^[A-Z0-9]{10}$ ]]; then
  echo "Key ID dosya adından okunamadı (beklenen: SubscriptionKey_XXXXXXXXXX.p8)." >&2
  echo "Okunan: '$KEY_ID' — dosya adını değiştirdiysen elle düzelt." >&2
  exit 1
fi

# Anahtarın gerçekten bir EC private key olduğunu yüklemeden önce doğrula;
# yanlış dosya yüklenirse hata ancak ilk satın almada ortaya çıkardı.
if ! grep -q "BEGIN PRIVATE KEY" "$P8"; then
  echo "Bu bir .p8 özel anahtarı değil gibi görünüyor." >&2
  exit 1
fi

echo "Key ID   : $KEY_ID"
echo "Issuer ID: $ISSUER"
echo

echo "-> anahtar yükleniyor"
scp -i "$SSH_KEY" "$P8" "$HOST:$DIR/secrets/AuthKey.p8"
ssh -i "$SSH_KEY" "$HOST" "chmod 600 $DIR/secrets/AuthKey.p8"

echo "-> .env güncelleniyor"
ssh -i "$SSH_KEY" "$HOST" "
  set -e
  cd $DIR
  cp .env .env.yedek-\$(date +%s)
  # Anahtar zaten varsa değerini değiştir, yoksa sonuna ekle.
  for pair in 'APPLE_KEY_ID=$KEY_ID' 'APPLE_ISSUER_ID=$ISSUER'; do
    name=\"\${pair%%=*}\"
    if grep -q \"^\$name=\" .env; then
      sed -i \"s|^\$name=.*|\$pair|\" .env
    else
      echo \"\$pair\" >> .env
    fi
  done
  chmod 600 .env
"

echo "-> servis yeniden başlatılıyor"
ssh -i "$SSH_KEY" "$HOST" "cd $DIR && docker compose up -d" >/dev/null 2>&1

sleep 8
echo "-> doğrulama"
ssh -i "$SSH_KEY" "$HOST" "
  docker exec dersdefteri-api sh -c 'test -s /secrets/AuthKey.p8' && echo '   anahtar container icinde OK'
  docker exec dersdefteri-api printenv APPLE_KEY_ID APPLE_ISSUER_ID | sed 's/^/   /'
"
curl -s -m 15 https://dersapi.adilemree.xyz/health && echo
echo
echo "Bitti. Kalan tek adım: App Store Connect'te uygulamayı kaydedince"
echo "sayısal Apple Kimliği'ni APPLE_APP_APPLE_ID olarak .env'e ekle."
