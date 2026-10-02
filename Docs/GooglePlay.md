# Ders Defteri — Google Play Yayınlama Rehberi

Android uygulaması `android/` klasöründe: Kotlin + Jetpack Compose ile yazılmış
native bir uygulama. iOS sürümüyle aynı ekranlara, aynı hesaplama mantığına ve
aynı sunucuya sahip. Bu dosya yalnızca **senin elle yapman gereken** adımları
içerir; bunlar Play Console ya da Google Cloud hesabına giriş gerektirir.

| | |
|---|---|
| Paket adı | `xyz.adilemree.dersdefteri` (sonradan **değiştirilemez**) |
| Sürüm | 1.0 (versionCode 1) |
| Android | 8.0 ve üzeri (minSdk 26), hedef API 36 |
| Ana ekrandaki ad | Ders Defterim (iPhone'daki gibi) |
| Mağaza görselleri | `android/store/` |
| Derleme çıktısı | `android/app/build/outputs/bundle/release/app-release.aab` |

---

## 0. Önce bunu yedekle — yükleme anahtarı

Play'e yüklenen her derleme bu anahtarla imzalanır. Dosyalar git'e girmez
(`.gitignore`), yani yalnızca bu Mac'te duruyorlar:

- `android/keystore/upload.jks`
- `android/keystore.properties` (şifre burada)

İkisini birlikte şifre yöneticine ya da güvendiğin bir buluta kopyala.
Kaybedersen Play Console'dan "yükleme anahtarını sıfırlama" talebi açılabilir,
ama günler sürer ve bu sürede güncelleme yükleyemezsin.

Yükleme anahtarının parmak izleri (aşağıda Google Cloud'da gerekecek):

```
SHA-1   BA:4D:75:F1:DC:76:B7:C9:EC:D0:51:A3:91:4D:2F:BF:67:96:0D:DA
SHA-256 51:6C:FC:EB:58:CB:C8:B4:6D:49:47:8F:8C:91:8E:CD:7C:46:8D:1E:DD:C8:F8:A8:3D:AD:80:7B:3C:70:8B:99
```

---

## Sıra

Adımlar birbirine bağlı; bu sırayla git:

1. Play Console'da uygulamayı oluştur, ödeme profilini bağla.
2. Google Cloud'da **Web istemci kimliğini** oluştur. Ben (ya da sen) uygulamaya
   ve sunucuya yazınca AAB yeniden derlenir.
3. AAB'yi **dahili teste** yükle. Play burada uygulama imzalama anahtarını
   oluşturur; onun SHA-1'iyle Android istemcilerini ekle.
4. Abonelikleri ve ömür boyu ürününü oluştur. Play, faturalandırma izni olan bir
   derleme yüklenmeden ürün sayfasını açmaz, bu yüzden 3. adımdan sonra gelir.
5. Hizmet hesabını oluştur. Satın almaları sunucu doğrulasın diye JSON
   dosyasını sunucuya koy.
6. Uygulama içeriği formlarını ve mağaza girişini doldur.
7. **Kapalı test:** en az 12 test kullanıcısı 14 gün boyunca katılımda kalsın.
8. Üretim erişimine başvur ve yayınla.

---

## 1. Play Console — uygulama kaydı

**Tüm uygulamalar → Uygulama oluştur**

- [ ] Uygulama adı: `Ders Defteri: Özel Ders Takibi` (30/30 karakter). Kısa
      isteyen için `Ders Defteri` da olur.
- [ ] Varsayılan dil: **Türkçe – tr-TR**
- [ ] Uygulama / Oyun: **Uygulama**
- [ ] Ücretsiz / Ücretli: **Ücretsiz** (uygulama içi satın almalar ücretsiz
      uygulamada olur; ücretsiz yayınlanan uygulama sonradan ücretliye çevrilemez)
- [ ] Beyanlar: Geliştirici Programı Politikaları ve ABD ihracat yasaları ✓

**Ödeme profili:** Ürün satabilmek için gerekli. **Ayarlar → Ödeme profili**
(ya da **Play ile para kazanın → Para kazanma kurulumu**) bölümünden banka
hesabını ve vergi bilgilerini gir. Doğrulama birkaç gün sürebilir. Erken başla.

---

## 2. Google Cloud — Google ile Giriş

Android uygulaması girişte Google'dan bir kimlik jetonu ister. Sunucu bu jetonu
**Web istemci kimliğine** göre doğrular. Android istemcileri ise yalnızca "bu
paket ve bu imza bu projeye ait" diye kayıt olur; kodda geçmezler.

[console.cloud.google.com](https://console.cloud.google.com) → yeni proje:
**Ders Defteri** (5. adımdaki hizmet hesabı da bu projede olacak).

**Google Auth Platform**

- [ ] **Markalama (Branding):** uygulama adı `Ders Defteri`, destek e-postası,
      geliştirici e-postası. Yetkili alan: `adilemree.xyz`. Ana sayfa
      `https://dersdefteri.adilemree.xyz`, gizlilik
      `https://dersdefteri.adilemree.xyz/gizlilik`, koşullar
      `https://dersdefteri.adilemree.xyz/kosullar`.
      Logo **yükleme**: logo, Google'ın marka doğrulamasını başlatır ve haftalar
      sürebilir. Logo olmadan da giriş çalışır.
- [ ] **Kitle (Audience):** Harici (External) → **Uygulamayı yayınla** (In
      production). "Test" durumunda yalnızca listeye eklenen hesaplar giriş
      yapabilir. Yalnızca temel kapsamlar (ad, e-posta) kullanıldığı için Google
      doğrulaması gerekmez.

**İstemciler (Clients) → İstemci oluştur**

| Tür | Ad | Paket adı | SHA-1 |
|---|---|---|---|
| Web uygulaması | Ders Defteri Sunucu | — | — |
| Android | Play imzası | `xyz.adilemree.dersdefteri` | Play'in uygulama imzalama anahtarı (3. adımda çıkar) |
| Android | Yükleme anahtarı | `xyz.adilemree.dersdefteri` | `BA:4D:75:F1:DC:76:B7:C9:EC:D0:51:A3:91:4D:2F:BF:67:96:0D:DA` |
| Android | Geliştirme | `xyz.adilemree.dersdefteri.debug` | `AD:46:94:91:09:16:E9:79:1A:1B:C2:95:C4:9C:77:DD:6B:98:A5:3F` |

- Web istemcisinde "Yetkili JavaScript kaynakları" ve "yönlendirme URI'leri"
  boş kalır.
- **Web istemci kimliği** (`…apps.googleusercontent.com`) iki yere yazılır.
  Gizli değildir, uygulamanın içinde de durur:
  - `android/gradle.properties` → `googleWebClientId=…apps.googleusercontent.com`
  - sunucu `.env` → `GOOGLE_WEB_CLIENT_ID=…apps.googleusercontent.com`
- "Yükleme anahtarı" istemcisi, Play'den değil doğrudan kurulan sürüm
  derlemeleri içindir. "Geliştirme" istemcisi bu Mac'teki hata ayıklama
  derlemesi içindir (`Ders Defterim Test` adıyla yanına kurulur).
- Kimlik boşken uygulama derlenir ve çalışır; yalnızca "Google ile Giriş"
  düğmesi "henüz yapılandırılmadı" der.

---

## 3. Dahili test — ilk yükleme

- [ ] 2. adımdaki Web istemci kimliği uygulamaya yazıldıktan sonra AAB'yi
      yeniden derle:
      ```
      cd android && JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:bundleRelease
      ```
- [ ] **Test edin ve yayınlayın → Test → Dahili test → Yeni sürüm oluştur**
- [ ] **Play Uygulama İmzalama**: varsayılanı kabul et (anahtarı Google yönetir).
- [ ] `app-release.aab` dosyasını yükle. Sürüm adı `1.0 (1)`, sürüm notu aşağıda.
- [ ] **Test kullanıcıları** sekmesi: kendi Gmail hesabını içeren bir e-posta
      listesi oluştur. Katılım bağlantısını telefonda aç ve uygulamayı Play'den
      kur.
- [ ] **Test edin ve yayınlayın → Kurulum → Uygulama imzalama** (App signing):
      "Uygulama imzalama anahtarı sertifikası" altındaki **SHA-1**'i kopyala.
      2. adımdaki tabloda "Play imzası" satırına bunu yaz. Bu istemci olmadan
      Play'den kurulan uygulamada Google ile giriş çalışmaz.

Her yeni yüklemede `android/app/build.gradle.kts` içindeki `versionCode`
artmalı (1, 2, 3…). Play aynı numarayı ikinci kez kabul etmez.

### Sürüm notu (1.0)

```
<tr-TR>
Ders Defteri artık Android'de. Özel ders veren öğretmenler için öğrenci, program, ödeme ve ödev defteri: haftalık dersler kendiliğinden planlanır, bakiye otomatik hesaplanır, biten dersi ana ekran widget'ından tek dokunuşla işaretlersin.
</tr-TR>
```

---

## 4. Ürünler — abonelikler ve ömür boyu

Ürün kimlikleri `ProStore.kt` (Android) ve sunucudaki `googleplay.ts` içinde
sabit. Ürünlerin **birebir aynı** olması gerekir, yoksa satın alma ekranı boş
açılır. Kimlikler iOS'takilerle aynı tutuldu.

**Play ile para kazanın → Ürünler → Abonelikler → Abonelik oluştur**

| Alan | Aylık | Yıllık |
|---|---|---|
| Ürün kimliği | `dersdefteri.abonelik.aylik` | `dersdefteri.abonelik.yillik` |
| Ad | Ders Defteri Pro Aylık | Ders Defteri Pro Yıllık |
| Temel plan kimliği | `aylik` | `yillik` |
| Tür | Otomatik yenilenen | Otomatik yenilenen |
| Faturalandırma dönemi | 1 ay | 1 yıl |
| Fiyat (Türkiye) | ₺129,99 | ₺899,99 |
| Teklif | — | `ucretsiz-deneme` |

- [ ] Her abonelikte **Avantajlar** (en fazla 4, Play'in abonelik ekranında
      görünür): `Sınırsız öğrenci` · `Cihazlar arası eşitleme` ·
      `PDF veli raporu` · `Günlük program özeti`
- [ ] Açıklama: `Eşitleme, sınırsız öğrenci ve PDF veli raporu`
- [ ] Temel planı **etkinleştir**, yoksa uygulama planı göremez.
- [ ] Yıllıkta **Teklif ekle**: kimlik `ucretsiz-deneme`. Uygunluk: **Yeni
      müşteri edinme → Hiç abonelik almamış olanlar**. Aşama: **Ücretsiz
      deneme, 1 hafta**. Teklifi etkinleştir. Uygulama denemeyi kendisi bulur;
      Play denemeyi yalnızca uygun kullanıcıya gösterir.
- Ek süre (grace period) ve hesap askıya alma varsayılan kalabilir. Sunucu
  ek sürede Pro'yu kapatmaz.

**Play ile para kazanın → Ürünler → Tek seferlik ürünler → Ürün oluştur**

| Alan | Değer |
|---|---|
| Ürün kimliği | `dersdefteri.omurboyu` |
| Ad | Ders Defteri Pro Ömür Boyu |
| Açıklama | Tek seferlik ödemeyle Pro'nun tüm özellikleri; abonelik yok. |
| Satın alma seçeneği | Satın al (kimlik ör. `satin-al`) |
| Fiyat (Türkiye) | ₺2.499,99 |

- [ ] Ürünü **etkinleştir**.

Diğer ülkelerin fiyatlarını Play otomatik çevirir. Yalnızca Türkiye'de
satacaksan onlara dokunma.

### Lisans testi (para ödemeden satın alma denemek)

**Ayarlar → Lisans testi** → kendi Gmail hesabını ekle, yanıt
`RESPOND_NORMALLY`. Bu hesapla yapılan satın almalar ücretsizdir ve "Test kartı,
her zaman onaylar" seçeneği çıkar. Test abonelikleri hızlı yenilenir: aylık 5
dakikada, yıllık 30 dakikada, deneme 3 dakikada. Altı yenilemeden sonra
kendiliğinden biter.

### Arkadaşlara ücretsiz Pro — promosyon kodu

**Play ile para kazanın → Promosyonlar → Promosyon kodları** → ömür boyu
ürünü (`dersdefteri.omurboyu`) için kod üret (çeyrek başına 500 kod).
Arkadaşın **Play Store → Profil → Ödemeler ve abonelikler → Kod kullan** ile
kodu kullanır. Uygulama açıldığında satın almayı görür, onaylar ve Pro açılır.
iOS'taki teklif kodlarının Android karşılığı budur.

---

## 5. Hizmet hesabı — satın almaları sunucu doğrulasın

Sunucu, telefonun "satın aldım" demesine güvenmez; satın alma jetonunu Google
Play Developer API'ye sorar. Bunun için bir hizmet hesabı gerekir. Hizmet
hesabı yoksa uygulamada satın alma yine çalışır, ama eşitleme için sunucu
Android aboneliğini tanımaz.

- [ ] Google Cloud (aynı proje) → **API'ler ve Hizmetler → Kitaplık** →
      **Google Play Android Developer API** → Etkinleştir.
- [ ] **IAM ve Yönetici → Hizmet hesapları → Oluştur**: ad `play-dogrulama`.
      Proje rolü verme, gerekmez.
- [ ] Hesabı aç → **Anahtarlar → Anahtar ekle → JSON** → dosya iner. Dosyayı
      kimseyle paylaşma, git'e koyma.
- [ ] Play Console → **Kullanıcılar ve izinler → Yeni kullanıcı davet et** →
      hizmet hesabının e-postası (`play-dogrulama@….iam.gserviceaccount.com`):
      - Uygulama izinleri → Ders Defteri → **Finansal verileri görüntüleme**
        ve **Siparişleri ve abonelikleri yönetme**.
- [ ] JSON dosyasını sunucuya koy (dosya adı tam olarak bu olmalı):
      ```
      scp -i ~/.ssh/id_ed25519_sevgili ~/Downloads/<indirilen>.json root@92.5.38.182:/opt/dersdefteri/secrets/google-play.json
      ssh -i ~/.ssh/id_ed25519_sevgili root@92.5.38.182 "chmod 600 /opt/dersdefteri/secrets/google-play.json"
      ```
      Sunucudaki `docker-compose.yml` bu yolu zaten gösteriyor. Dosyayı koyduktan
      sonra servisin yeniden başlatılması yeterli.

İzinlerin Google tarafında etkinleşmesi **24 saate kadar** sürebilir. Bu
sürede sunucu "yetki yok" hatası alır. Bilinen bir kestirme: Play Console'da
herhangi bir ürünün açıklamasını değiştirip kaydetmek izinleri çoğu zaman
hemen tazeler.

### Gerçek zamanlı bildirimler (isteğe bağlı, önerilir)

Bunlar olmadan da iptal ve iadeler öğrenilir: sunucu Android satın almalarını
Play'e kendisi sorar (süresi geçmiş kayıtları saatte, etkin olanları günde
bir). Bildirimler bunu anında yapar.

- [ ] Rastgele bir değer üret: `openssl rand -hex 24`. Değeri sunucu
      `.env`'ine `GOOGLE_RTDN_TOKEN=<değer>` olarak yaz.
- [ ] Google Cloud → **Pub/Sub → Konu oluştur**: `play-bildirimleri`.
- [ ] Konunun izinlerine `google-play-developer-notifications@system.gserviceaccount.com`
      hesabını **Pub/Sub Yayıncısı** rolüyle ekle.
- [ ] Konuya **abonelik** ekle: teslim türü **Push**, uç nokta:
      ```
      https://dersapi.adilemree.xyz/v1/webhooks/googleplay?token=<değer>
      ```
- [ ] Play Console → **Play ile para kazanın → Para kazanma kurulumu →
      Gerçek zamanlı geliştirici bildirimleri**: konu adı
      `projects/<proje-kimliği>/topics/play-bildirimleri`, bildirim içeriği
      **abonelikler, iptal edilen satın almalar ve tüm tek seferlik ürünler** →
      **Test bildirimi gönder**.

---

## 6. Uygulama içeriği ve mağaza girişi

**Politika ve programlar → Uygulama içeriği**

| Form | Yanıt |
|---|---|
| Gizlilik politikası | `https://dersdefteri.adilemree.xyz/gizlilik` |
| Uygulama erişimi | Tüm işlevler özel erişim gerekmeden kullanılabilir. Giriş isteğe bağlıdır ve herhangi bir Google hesabıyla yapılır. |
| Reklamlar | Hayır, reklam içermiyor |
| Reklam kimliği | Hayır, kullanmıyor |
| İçerik derecelendirmesi | Anket aşağıda |
| Hedef kitle | **Yalnızca 18 yaş ve üzeri**. Çocuklara hitap etme olasılığı: Hayır |
| Haber uygulaması | Hayır |
| Finansal özellikler | Hiçbiri (yalnızca kayıt tutuyor, para transferi yok) |
| Sağlık | Sağlık uygulaması değil |
| Devlet uygulaması | Hayır |

**İçerik derecelendirmesi (IARC):** kategori **Yardımcı program, verimlilik,
iletişim veya diğer**. Şiddet, cinsellik, küfür, uyuşturucu ve kumar: hepsi
Hayır. Kullanıcılar birbiriyle etkileşebilir mi: Hayır. Konum paylaşımı:
Hayır. Dijital ürün satın alma: **Evet**. Sonuç her yerde 3+ olmalı.

**Veri güvenliği.** iOS'taki Gizlilik Beslemesi ile tutarlı:

- Veri toplanıyor mu: **Evet**. Aktarım sırasında şifreleniyor mu: **Evet**
  (HTTPS).
- Hesap oluşturma yöntemi: **OAuth** (Google ile Giriş).
- Hesap silme bağlantısı: `https://dersdefteri.adilemree.xyz/hesap-silme`.
  Bu sayfa sunucu güncellemesiyle yayına girer; formu göndermeden önce açıldığını
  kontrol et.
- Hesabı silmeden veri silme: **Evet**. Her kayıt (öğrenci, ders, ödeme, ödev)
  uygulamadan silinebilir; eşitleme açıksa sunucudan da silinir.

| Veri türü | Toplanıyor | Paylaşılıyor | Zorunlu mu | Amaç |
|---|---|---|---|---|
| Kişisel bilgiler → Ad | ✓ | ✗ | İsteğe bağlı | Uygulama işlevselliği, hesap yönetimi |
| Kişisel bilgiler → E-posta adresi | ✓ | ✗ | İsteğe bağlı | Uygulama işlevselliği, hesap yönetimi |
| Kişisel bilgiler → Kullanıcı kimlikleri | ✓ | ✗ | İsteğe bağlı | Uygulama işlevselliği, hesap yönetimi |
| Finansal bilgiler → Satın alma geçmişi | ✓ | ✗ | İsteğe bağlı | Uygulama işlevselliği |
| Uygulama etkinliği → Kullanıcının oluşturduğu diğer içerik | ✓ | ✗ | İsteğe bağlı | Uygulama işlevselliği |

"İsteğe bağlı" denmesinin nedeni şu: eşitlemeyi açmayan kullanıcıdan hiçbir
veri sunucuya gitmez. Defterdeki öğrenci adları ve veli telefonları
"kullanıcının oluşturduğu içerik" sayılır. Hiçbiri geçici işlenmiyor, üçüncü
tarafla paylaşılmıyor. Analiz, reklam ya da çökme raporu SDK'sı yok.

**Mağaza girişi → Ana mağaza girişi**

- [ ] Uygulama simgesi: `android/store/play-icon-512.png`
- [ ] Öne çıkan görsel: `android/store/feature-graphic.png` (1024×500)
- [ ] Telefon ekran görüntüleri: `android/store/screenshots/01…06` (1080×1920).
      Sıra önemli; ilk ikisi en çok görülenler.
- [ ] Kategori: **Eğitim**. İletişim e-postası `destek@adilemree.xyz`, web sitesi
      `https://dersdefteri.adilemree.xyz`.
- [ ] **Mağaza ayarları → Ülkeler/bölgeler**: App Store'daki seçiminle aynı
      (en azından Türkiye).

### Kısa açıklama (80 karakter sınırı)

```
Özel ders verenler için öğrenci, program, ödeme ve ödev takibi tek defterde.
```

### Tam açıklama (4000 karakter sınırı)

```
Özel ders veren öğretmenler için hazırlanmış bir defter. Öğrencilerini, ders programını, ödemeleri ve ödevleri tek yerde tut; kimin borcu olduğunu ve kimin ne zaman geleceğini bir bakışta gör.

ÖĞRENCİLER
• Ders, sınıf, saatlik ücret ve veli iletişim bilgileri
• Her öğrenci için ders geçmişi, ödeme dökümü ve bakiye
• Tek dokunuşla ara, SMS gönder veya WhatsApp'tan yaz
• Tüm kayıtlarını CSV olarak dışa aktar; Excel ve Google E-Tablolar ile açılır

DERS PROGRAMI
• Haftalık ve aylık takvim görünümü
• Dersi "Her hafta" olarak kaydet; sonraki haftalar kendiliğinden planlansın
• Dersi işlendi ya da iptal olarak işaretle; iptal sebebini kaydet
• Ana ekran widget'ı: sıradaki ders bir bakışta, biten dersi uygulamayı açmadan işaretle
• Saati geçip işaretlenmeyen dersler Özet'te seni bekler

ÜCRET VE ÖDEME
• Saatlik ücret ya da derse özel sabit tutar
• İşlenen her ders o günkü ücretle kilitlenir; fiyat güncellemen geçmişi bozmaz
• Toplu ödeme: aylık ya da haftalık anlaşmalarda ödemeyi derslere bağla, hangi dersin ödendiğini gör
• Nakit, havale ve diğer ödeme yöntemleri
• Borç ve avans otomatik hesaplanır, ödeme hatırlatma mesajı hazır gelir

ÖDEVLER
• Ödev ver, teslim tarihi belirle, tamamlananları işaretle
• Teslimden bir gün önce, teslim günü ve gecikirse hatırlatma bildirimi

HATIRLATMALAR
• Her ders için, seçtiğin süre kadar önce bildirim
• Uygulama tamamen çevrimdışı çalışır; internet gerekmez

DERS DEFTERİ PRO
Ücretsiz sürümde 2 aktif öğrenciye kadar tüm temel özellikler açıktır.
Pro ile şunlar gelir (aylık, yıllık ya da tek seferlik ömür boyu):
• Sınırsız öğrenci
• Cihazlar arası eşitleme — defterin hesabına yedeklenir, yeni telefonda Google hesabınla giriş yapman yeterli
• PDF veli raporu — dersleri, ödevleri ve bakiyeyi tek belgede veliye gönder
• Günlük program özeti — her sabah o günün tüm dersleri tek bildirimde

ABONELİK VE ÖMÜR BOYU
Ders Defteri Pro aylık veya yıllık otomatik yenilenen bir abonelik ya da tek seferlik ömür boyu satın alma olarak sunulur. Ömür boyu satın alma yenilenmez. Ücret, satın alma onaylandığında Google Play hesabınızdan tahsil edilir. Abonelik, iptal edilmediği sürece her dönemin sonunda otomatik olarak yenilenir. Aboneliğinizi Google Play Store'da Profil > Ödemeler ve abonelikler > Abonelikler bölümünden dilediğiniz zaman yönetebilir veya iptal edebilirsiniz. Ücretsiz deneme sunulduğunda, deneme bitmeden iptal ederseniz ücret alınmaz.

GİZLİLİK
Kayıtlarınız cihazınızda tutulur. Eşitlemeyi açtığınızda şifreli bağlantı üzerinden yalnızca sizin hesabınıza yedeklenir. Reklam yok, izleme yok, veri satışı yok. Hesabınızı ve tüm kayıtlarınızı uygulama içinden kalıcı olarak silebilirsiniz.

Kullanım Koşulları: https://dersdefteri.adilemree.xyz/kosullar
Gizlilik Politikası: https://dersdefteri.adilemree.xyz/gizlilik
```

---

## 7. Kapalı test — 12 kişi, 14 gün

2023'ten sonra açılan **kişisel** geliştirici hesaplarında üretime çıkmadan önce
bu şart. Dahili test bu süreye sayılmaz.

- [ ] **Test edin ve yayınlayın → Test → Kapalı test → Kanal oluştur** (ör.
      "Arkadaşlar"). Dahili testteki sürümü buraya **yükselt** (promote) ya da
      aynı AAB'yi yükle. Ülke olarak Türkiye'yi seç.
- [ ] Test kullanıcıları: en az **12** Gmail adresi içeren bir e-posta listesi
      ya da Google Grubu. Adresler Android telefonda Play'e giriş yapılmış
      hesaplar olmalı.
- [ ] Sürümü incelemeye gönder. İlk incelemede uygulamanın kendisi de
      incelenir; birkaç gün sürebilir.
- [ ] Onaydan sonra **katılım bağlantısını** paylaş. Her kişi bağlantıdan "Test
      kullanıcısı ol"a basmalı ve uygulamayı Play'den kurmalı. Sayaç, 12. kişi
      katıldığında başlar. 14 gün boyunca kimse ayrılmamalı.
- [ ] Bu sürede testçilerin uygulamayı gerçekten kullanması ve geri bildirim
      vermesi işine yarar. Üretim başvurusunda Google bunu soruyor (testçileri
      nasıl buldun, ne geri bildirim aldın, neyi değiştirdin).
- [ ] Süre dolunca **Kontrol paneli → Üretim erişimine başvur**. Yanıt
      genelde 7 gün içinde gelir. Sonra **Üretim → Yeni sürüm** ile yayınla.

Promosyon kodları kapalı testteki kullanıcılarda da çalışır. Testçilere ömür
boyu kodu vermek hem teşekkür hem gerçek satın alma akışının denemesi olur.

---

## 8. Sunucu — Android için güncelleme

Kod hazır ama **henüz canlıda değil**. Onay verdiğinde kurulum şöyle olacak:

1. Veritabanı yedeği (`pg_dump`).
2. Kod kopyalanır. Veritabanı geçişi `20261002000000_google_play` uygulanır:
   `users` tablosuna `googleSub` eklenir ve `appleSub` boş bırakılabilir hâle
   gelir. `subscriptions` tablosuna `store` ve `lastCheckedAt` eklenir.
   Yalnızca ekleme yapıldığı için yayındaki iOS sürümü etkilenmez.
3. Container yeniden derlenir. Hesap silme sayfası `/hesap-silme` ile
   güncellenmiş gizlilik politikası ve destek sayfası yayına girer.

Sonrasında `.env`'e eklenecekler (2. ve 5. adımlardan):

```
GOOGLE_WEB_CLIENT_ID=…apps.googleusercontent.com
GOOGLE_RTDN_TOKEN=…            # isteğe bağlı, gerçek zamanlı bildirimler
```

`GOOGLE_PLAY_PACKAGE` varsayılanı zaten `xyz.adilemree.dersdefteri`.
Hizmet hesabının yolu `docker-compose.yml` içinde tanımlı.

---

## Bilinmesi gerekenler

**iPhone ve Android hesapları ayrı.** iPhone'da Apple ile, Android'de Google ile
giriş yapılıyor. Sunucu bunları iki ayrı hesap olarak görür. İki platformu
birden kullanan biri defterini ikisi arasında eşitleyemez. App Store'dan aldığı
Pro da Android'de açılmaz (tersi de geçerli). İleride gerekirse hesap bağlama
eklenebilir; ilk sürüm için bilinçli olarak dışarıda bırakıldı.

**Pro'nun kilitledikleri iOS ile aynı:** eşitleme, 2'den fazla aktif öğrenci,
PDF veli raporu, günlük program özeti. Haftalık dersler ve CSV dışa aktarma
herkese açık.

**Bildirimler.** Android 13 ve üzerinde bildirim izni tanıtım ekranından sonra
sorulur. Ders hatırlatmaları en fazla 10 dakika sapmayla gelir. Ayarlar →
**Tam zamanında hatırlat** açılırsa tam dakikasında gelir (Android bu izni
kullanıcıya bırakıyor).

**Yedekleme.** Android'in kendi yedeği (Google hesabı yedeği ve telefon
taşıma) defteri de taşır. Oturum bilgileri bilerek yedeğe girmez; yeni
telefonda yeniden giriş yapılır.

**Geliştirme derlemesi.** `Ders Defterim Test` adıyla mağaza sürümünün yanına
kurulur ve onun verisine dokunmaz. Ekran görüntüleri için örnek defter
yalnızca bu derlemede var, mağaza sürümüne girmiyor.
