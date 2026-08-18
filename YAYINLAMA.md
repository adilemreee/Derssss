# Ders Defteri — App Store Yayınlama Rehberi

Kod tarafı hazır. Bu dosya yalnızca **senin elle yapman gereken** adımları içerir;
hiçbiri koddan yapılamaz çünkü Apple hesabına giriş gerektirir.

---

## 1. Apple Developer portalı

**Certificates, Identifiers & Profiles → Identifiers → `adilemre.ONOOOO`**

- [ ] **Sign In with Apple** özelliğini işaretle ve kaydet.
      (Uygulama artık bunu kullanıyor; açık değilse giriş çalışmaz.)
- [ ] iCloud, Push Notifications ve App Groups artık kullanılmıyor —
      işaretliyse kaldırabilirsin.

(`.p8` anahtarı burada değil, App Store Connect'te üretilir — 2. adımın
sonuna bak.)

---

## 2. App Store Connect — uygulama kaydı

- [ ] Yeni uygulama oluştur: bundle `adilemre.ONOOOO`, ad **Ders Defteri**,
      birincil dil **Türkçe**, kategori **Eğitim**.
- [ ] Uygulama oluşunca **Apple Kimliği** (sayısal, ör. `6740123456`) verilir —
      bunu not et, 4. adımda gerekli.

**Abonelikler → yeni abonelik grubu: "Ders Defteri Pro"**

> `One.storekit` dosyası **yalnızca yerel testler içindir** ve App Store
> Connect'e hiçbir şey göndermez. Senkronizasyon tek yönlüdür: App Store
> Connect → Xcode. Yani aşağıdaki ürünleri App Store Connect'te elle
> oluşturman gerekir.

Ürün kimlikleri `ProStore.swift` içinde sabit — **birebir aynı** olmalı,
yoksa uygulama ürünleri bulamaz ve paywall boş açılır.

| Alan | Aylık | Yıllık |
|---|---|---|
| Ürün Kimliği | `dersdefteri.pro.monthly` | `dersdefteri.pro.yearly` |
| Referans adı | Pro Aylık | Pro Yıllık |
| Süre | 1 ay | 1 yıl |
| Fiyat | ₺79,99 | ₺599,99 |
| Görünen ad (tr) | Ders Defteri Pro Aylık | Ders Defteri Pro Yıllık |
| Açıklama (tr) | Eşitleme, sınırsız öğrenci ve PDF veli raporu | Eşitleme, sınırsız öğrenci ve PDF veli raporu |
| Aile paylaşımı | Kapalı | Kapalı |
| Tanıtım teklifi | — | 1 hafta ücretsiz deneme |

- [ ] Abonelik grubuna görsel + yerelleştirilmiş açıklama ekle (Apple zorunlu).
- [ ] Her iki ürün için **inceleme ekran görüntüsü** yükle (Apple zorunlu).
- [ ] Ürünler "Gönderilmeye Hazır" durumuna gelmeden build ile birlikte
      incelemeye gönderilmez.

**İpucu:** Ürünleri App Store Connect'te oluşturduktan sonra Xcode'da
`One.storekit` dosyasını açıp "Sync with App Store Connect" ile bağlarsan
yerel test ortamın gerçek yapılandırmayı yansıtır ve iki yerde ayrı ayrı
güncelleme derdi kalmaz.

**In-App Purchase anahtarı (`.p8`) — abonelik takibi için**

Menü yolu: **Kullanıcılar ve Erişim → Integrations → Keys → In-App Purchase**
(Apple Developer portalındaki "Keys" bölümü değil; oradaki anahtarlar APNs
içindir ve bu iş için çalışmaz.)

- [ ] `+` ile yeni anahtar üret, adına "Ders Defteri Sunucu" gibi bir şey ver.
- [ ] İndir. **Yalnızca bir kez indirilebilir** — kaybedersen iptal edip
      yenisini üretmen gerekir.
- [ ] Dosya adı `SubscriptionKey_XXXXXXXXXX.p8` biçiminde olur; sondaki
      `XXXXXXXXXX` senin **Key ID**'in.
- [ ] Aynı sayfadaki **Issuer ID**'yi kopyala (UUID biçiminde).

Bu adım için Account Holder veya Admin rolü gerekir.

**Uygulama bilgileri** — hazır metinler [AppStoreConnect-Metinler.md](AppStoreConnect-Metinler.md) dosyasında

- [ ] Gizlilik Politikası URL: `https://dersdefteri.adilemree.xyz/gizlilik`
- [ ] Destek URL: `https://dersdefteri.adilemree.xyz/destek`
- [ ] Kullanım Koşulları (EULA) URL: `https://dersdefteri.adilemree.xyz/kosullar`

**Gizlilik Beslemesi (App Privacy)** — `PrivacyInfo.xcprivacy` ile tutarlı doldur:
E-posta adresi, Ad, Kullanıcı Kimliği, Diğer Kullanıcı İçeriği, Satın Alma Geçmişi.
Hepsi "Uygulama İşlevselliği" amaçlı, **izleme yok**, hepsi kimliğe bağlı.

---

## 3. İnceleme notu (App Review'a yazılacak)

Apple, abonelikli ve hesap açılan uygulamalarda test hesabı ister.
Apple ile Giriş kullanıldığı için ayrı hesap gerekmez; şu notu yaz:

Hazır metin [AppStoreConnect-Metinler.md](AppStoreConnect-Metinler.md) içindeki
**Notes** bölümünde — olduğu gibi yapıştırabilirsin.

---

## 4. Sunucu son ayarları

### ✅ Yapıldı — In-App Purchase anahtarı

`SubscriptionKey_6F4RU5BV2K.p8` sunucuya kuruldu ve **Apple'a karşı canlı test
edildi**: App Store Server API çağrısı kimlik doğrulamasını geçti (dönen hata
`4000006 Invalid transaction id`, yani iş mantığı hatası — kimlik kabul edildi).

```
APPLE_KEY_ID    = 6F4RU5BV2K
APPLE_ISSUER_ID = b3dd452c-f655-429e-a773-a2da90dad8d4
anahtar         = /opt/dersdefteri/secrets/AuthKey.p8 (izin 600)
```

### ✅ Sunucu yapılandırması tamamlandı

```
APPLE_BUNDLE_ID      = adilemre.ONOOOO
APPLE_APP_APPLE_ID   = 6802450803
APPLE_KEY_ID         = 6F4RU5BV2K
APPLE_ISSUER_ID      = b3dd452c-f655-429e-a773-a2da90dad8d4
APPSTORE_ENVIRONMENT = Production
```

`.env` içinde boş kalan değişken yok. Ortam `Production` ama TestFlight
satın almaları Sandbox imzalı gelir — kod imzalı işlemi iki ortamda da
denediği için ikisi de çalışır.

### App Store Server Notifications (webhook)

App Store Connect → uygulamanı seç → **General → App Information →
App Store Server Notifications**. Sürüm **Version 2**, hem Production hem
Sandbox URL alanına:

```
https://dersapi.adilemree.xyz/v1/webhooks/appstore
```

Uç canlı ve imzasız istekleri reddediyor (test edildi: `400 invalid_signature`).

---

## 5. Arşivle ve yükle

Xcode'da: **Product → Archive** → Distribute App → App Store Connect.

Arşivden önce imzalamanın açık olduğundan emin ol (bu oturumda derlemeler
`CODE_SIGNING_ALLOWED=NO` ile yapıldı, Xcode arayüzünden arşivlerken bu geçerli
değil).

---

## Bilinmesi gerekenler

**Pro şu an neyi kilitliyor?** Beş şey:

| Özellik | Ücretsiz | Pro |
|---|---|---|
| Cihazlar arası eşitleme | — | ✓ |
| Aktif öğrenci | 2 | Sınırsız |
| Tekrarlayan ders şablonu | 1 | Sınırsız |
| PDF veli raporu | — | ✓ |
| Günlük program özeti bildirimi | — | ✓ |
| CSV dışa aktarma | — | ✓ |

**Hesap artık isteğe bağlı.** Eşitleme Pro'ya ait olduğu için ücretsiz
kullanıcının hesaba ihtiyacı yok; uygulama girişsiz açılıyor ve defter cihazda
tutuluyor. Apple, kullanılamayan bir özellik için kayıt zorunlu tutmayı
yönerge 5.1.1(v) ile reddediyor — bu yüzden giriş ekranı bir kapı değil,
Ayarlar → Eşitleme altından açılan isteğe bağlı bir sayfa.

**Dikkat:** Ücretsiz kullanıcının hiçbir yedekleme yolu yok (CSV de Pro).
Telefon kaybolursa kayıtlar gider. Uygulama içinde bunu açıkça yazdım, ama
kötü yorum riskini azaltmak için CSV dışa aktarmayı ücretsiz yapmayı
değerlendirebilirsin.

Abonelik açıklamalarını (App Store Connect'te gireceğin metinler) bu listeyle
tutarlı yaz; `One.storekit` içindeki açıklamalar zaten güncellendi.

**Veri geçişi:** Bu sürüm CloudKit'ten kendi sunucuna geçiyor. Uygulamayı
zaten kullanan biri güncelledikten sonra cihazındaki kayıtlar duruyor ve giriş
yapınca sunucuya yükleniyor — fakat **başka bir cihazdaki** CloudKit verisi
gelmiyor. Mevcut kullanıcın varsa bunu onlara duyur.

**Yedekleme:** Yeni veritabanı `sevgiliapp` yedek betiğine dahil değil.
`/opt/sevgiliapp/backup.sh` içine `dersdefteri` veritabanını da eklemek
isteyebilirsin.
