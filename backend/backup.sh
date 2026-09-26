#!/usr/bin/env bash
set -euo pipefail

# === Ders Defteri Otomatik Yedekleme ===
# /opt/sevgiliapp/backup.sh ile aynı yapı.
# Yedekler: Postgres (dersdefteri veritabanı) + Config/Secrets
# Redis ayrıca alınmaz: aynı Redis container'ı (indeks 1) sevgiliapp
#   yedeğindeki redis.tar.gz içinde zaten var.
# Bildirim: ntfy (bildirim komutu)
# Retention: son 2 gün
# Cron: 5 0 * * * /opt/dersdefteri/backup.sh >> /var/log/dersdefteri-backup.log 2>&1

BACKUP_DIR="/opt/backups/dersdefteri"
RETENTION_DAYS=2
PG_USER="sevgili"
PG_DB="dersdefteri"
PG_CONTAINER="sevgiliapp-postgres-1"
APP_DIR="/opt/dersdefteri"

TS=$(date +%Y-%m-%d_%H%M)
DEST="$BACKUP_DIR/dersdefteri-$TS"
START=$(date +%s)

# Yedekte .env ve Apple anahtarları var; yalnızca root okuyabilmeli.
umask 077

cleanup() {
    local exit_code=$?
    local end
    end=$(date +%s)
    local duration=$((end - START))

    if [ $exit_code -eq 0 ]; then
        local size
        size=$(du -sh "$DEST" 2>/dev/null | cut -f1)
        bildirim bilgi "Ders Defteri yedekleme ✅" \
"Yedek: ${TS}
Boyut: ${size:-?}
Süre: ${duration}s"
    else
        bildirim kritik "Ders Defteri yedekleme BASARISIZ" \
"Yedek: ${TS}
Cikis kodu: ${exit_code}
Sure: ${duration}s

Log: /var/log/dersdefteri-backup.log"
    fi
}
trap cleanup EXIT

echo "=== Ders Defteri yedekleme başlıyor: $TS ==="

mkdir -p "$DEST"

# ── 1. Postgres dump ──
echo "[1/2] Postgres dump..."
docker exec "$PG_CONTAINER" pg_dump -U "$PG_USER" -Fc "$PG_DB" > "$DEST/pg_dump.dump"
echo "  -> $(du -sh "$DEST/pg_dump.dump" | cut -f1)"

# Doğrula (docker cp + içeride kontrol, stdin sorunu yok)
docker cp "$DEST/pg_dump.dump" "$PG_CONTAINER:/tmp/dd_verify.dump" 2>/dev/null
if docker exec "$PG_CONTAINER" pg_restore -l /tmp/dd_verify.dump > /dev/null 2>&1; then
    echo "  -> doğrulandı ✅"
else
    echo "  -> doğrulama HATASI ❌"
    docker exec "$PG_CONTAINER" rm -f /tmp/dd_verify.dump 2>/dev/null
    false  # backup'ı abort et
fi
docker exec "$PG_CONTAINER" rm -f /tmp/dd_verify.dump 2>/dev/null

# ── 2. Config + Secrets ──
echo "[2/2] Config & secrets..."
tar czf "$DEST/config.tar.gz" -C "$APP_DIR" \
    docker-compose.yml .env secrets/ 2>/dev/null
echo "  -> $(du -sh "$DEST/config.tar.gz" | cut -f1)"

# ── Eski yedekleri temizle ──
deleted=$(find "$BACKUP_DIR" -maxdepth 1 -mindepth 1 -type d -mtime +$RETENTION_DAYS -exec rm -rf {} \; -print 2>/dev/null | wc -l)
echo "🧹 $deleted eski yedek temizlendi (son $RETENTION_DAYS gün saklanıyor)"

echo "=== Tamam ✅  $DEST ==="
