import type { FastifyInstance } from 'fastify';
import { config } from '../config.js';

/**
 * App Store Connect gizlilik politikası ve destek adresi ister; otomatik
 * yenilenen abonelikte ayrıca kullanım koşulları (EULA) bağlantısı da zorunlu.
 * Üçüncü bir servise bağımlı kalmamak için bu üç sayfa API ile aynı yerden
 * servis edilir.
 */

const LAST_UPDATED = '2 Ekim 2026';

function page(title: string, body: string): string {
  return `<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title} — Ders Defteri</title>
<style>
  :root { color-scheme: light dark; }
  body {
    font: 16px/1.7 -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    max-width: 42rem; margin: 0 auto; padding: 2.5rem 1.25rem 5rem;
    color: #1c1b18; background: #faf8f3;
  }
  @media (prefers-color-scheme: dark) {
    body { color: #ece9e2; background: #16150f; }
    a { color: #8ab4f8; }
  }
  h1 { font-size: 1.75rem; margin-bottom: .25rem; }
  h2 { font-size: 1.1rem; margin-top: 2rem; }
  .meta { opacity: .6; font-size: .9rem; margin-bottom: 2rem; }
  ul { padding-left: 1.25rem; }
  li { margin: .35rem 0; }
</style>
</head>
<body>
<h1>${title}</h1>
<p class="meta">Ders Defteri · Son güncelleme: ${LAST_UPDATED}</p>
${body}
</body>
</html>`;
}

export async function legalRoutes(app: FastifyInstance) {
  app.get('/privacy', async (_request, reply) => {
    reply.type('text/html; charset=utf-8').send(
      page(
        'Gizlilik Politikası',
        `
<p>Ders Defteri, özel ders veren öğretmenlerin öğrenci, ders, ödeme ve ödev
kayıtlarını tuttuğu bir uygulamadır. Bu politika, uygulamanın hangi verileri
işlediğini açıklar.</p>

<h2>Toplanan veriler</h2>
<ul>
  <li><strong>Hesap bilgisi (yalnızca eşitlemeyi açarsanız):</strong> iPhone'da
  Apple ile Giriş kullanıldığında Apple'ın bize verdiği kalıcı kullanıcı kimliği
  ve —paylaşmayı seçtiyseniz— e-posta adresiniz; Apple'ın "E-postamı Gizle"
  seçeneğini kullanırsanız gerçek adresinizi hiçbir zaman görmeyiz. Android'de
  Google ile Giriş kullanıldığında Google hesabınızın kalıcı kimliği, e-posta
  adresiniz ve adınız.</li>
  <li><strong>Uygulama içeriği:</strong> Uygulamaya girdiğiniz öğrenci adları,
  ders programı, ücret ve ödeme kayıtları, ödevler ve notlar.</li>
  <li><strong>Abonelik durumu:</strong> App Store'un ya da Google Play'in ürettiği
  satın alma kimliği ve aboneliğin bitiş tarihi. Ödeme bilgileriniz Apple'da ya
  da Google'da kalır; kart numaranız bize hiçbir zaman ulaşmaz.</li>
</ul>

<h2>Verilerin kullanımı</h2>
<p>Veriler yalnızca uygulamanın çalışması için işlenir: cihazlarınız arasında
eşitleme ve abonelik durumunun doğrulanması. Verileriniz reklam amacıyla
kullanılmaz, satılmaz ve üçüncü taraflara pazarlama amacıyla aktarılmaz.</p>

<h2>Saklama ve güvenlik</h2>
<p>Veriler geliştiricinin kendi sunucusunda, şifreli bağlantı (HTTPS) üzerinden
iletilerek saklanır. Erişim, giriş yapan hesapla sınırlıdır: her kayıt yalnızca
onu oluşturan hesaba görünür.</p>

<h2>Öğrenci ve veli bilgileri</h2>
<p>Uygulamaya üçüncü kişilere (öğrenciler, veliler) ait ad ve telefon bilgisi
girebilirsiniz. Bu bilgilerin girilmesi ve güncel tutulması konusundaki
sorumluluk, kayıtları oluşturan öğretmene aittir. Bu veriler yalnızca sizin
hesabınız içinde görünür.</p>

<h2>Verilerinizi silme</h2>
<p>Uygulama içinden <em>Ayarlar → Eşitleme ve Hesap → Hesabı Sil</em> adımlarıyla
hesabınızı ve tüm kayıtlarınızı kalıcı olarak silebilirsiniz. Silme işlemi
geri alınamaz ve sunucudaki tüm verilerinizi kapsar. Uygulamaya erişiminiz
yoksa <a href="${config.PUBLIC_BASE_URL}/hesap-silme">hesap silme sayfasındaki</a>
adımlarla e-postayla da talep edebilirsiniz.</p>

<h2>İletişim</h2>
<p>Sorularınız için: <a href="mailto:${config.SUPPORT_EMAIL}">${config.SUPPORT_EMAIL}</a></p>
`,
      ),
    );
  });

  app.get('/terms', async (_request, reply) => {
    reply.type('text/html; charset=utf-8').send(
      page(
        'Kullanım Koşulları',
        `
<h2>Hizmet</h2>
<p>Ders Defteri, özel ders kayıtlarınızı tutmanız için sunulan bir uygulamadır.
Uygulamayı yürürlükteki mevzuata uygun şekilde kullanmayı kabul edersiniz.</p>

<h2>Abonelik ve ömür boyu satın alma</h2>
<ul>
  <li>Ders Defteri Pro, aylık veya yıllık otomatik yenilenen bir abonelik ya da
  tek seferlik ömür boyu satın alma olarak sunulur.</li>
  <li>Ücret, satın alma onaylandığında App Store ya da Google Play hesabınızdan
  tahsil edilir.</li>
  <li>Abonelik, mevcut dönemin bitiminden en az 24 saat önce iptal edilmediği
  sürece otomatik olarak yenilenir ve aynı tutar üzerinden ücretlendirilir.</li>
  <li>Aboneliğinizi iPhone'da <em>Ayarlar → Apple Kimliği → Abonelikler</em>,
  Android'de <em>Google Play → Profil → Ödemeler ve abonelikler → Abonelikler</em>
  üzerinden dilediğiniz zaman yönetebilir veya iptal edebilirsiniz.</li>
  <li>Ücretsiz deneme süresi sunulduğunda, deneme bitmeden abonelik iptal
  edilmezse ücretli döneme geçilir. Denemenin kullanılmayan kısmı, abonelik
  satın alındığında sona erer.</li>
  <li>Ömür boyu satın alma tek seferlik bir ödemedir, yenilenmez ve iptal
  edilmesi gerekmez. Aynı Apple Kimliği (iPhone) ya da aynı Google hesabı
  (Android) ile kullanılan cihazlarda <em>Satın Alımları Geri Yükle</em> ile
  tekrar etkinleştirilebilir.</li>
</ul>

<h2>Ücretsiz sürüm</h2>
<p>Pro olmadan uygulama sınırlı sayıda aktif öğrenci ile kullanılabilir.
Aboneliğiniz sona erdiğinde verileriniz silinmez; sınırın üzerindeki kayıtlar
görüntülenmeye devam eder, yeni kayıt ekleme kısıtlanır.</p>

<h2>İçerik sorumluluğu</h2>
<p>Uygulamaya girdiğiniz tüm veriler sizin sorumluluğunuzdadır. Üçüncü kişilere
ait bilgileri girerken gerekli izinlere sahip olduğunuzu kabul edersiniz.</p>

<h2>Sorumluluğun sınırı</h2>
<p>Uygulama "olduğu gibi" sunulur. Veri kaybı, hizmet kesintisi veya hatalı
hesaplamalardan doğabilecek dolaylı zararlardan geliştirici sorumlu tutulamaz.
Önemli kayıtlarınızın yedeğini CSV dışa aktarma özelliğiyle almanız önerilir.</p>

<h2>İletişim</h2>
<p><a href="mailto:${config.SUPPORT_EMAIL}">${config.SUPPORT_EMAIL}</a></p>
`,
      ),
    );
  });

  /// Hesap ve veri silme. Google Play, hesap açılabilen uygulamalardan
  /// uygulama dışından da silme talep edilebilecek bir sayfa ister.
  app.get('/hesap-silme', async (_request, reply) => {
    reply.type('text/html; charset=utf-8').send(
      page(
        'Hesap ve Veri Silme',
        `
<p>Ders Defteri'nde hesap yalnızca cihazlar arası eşitleme için açılır. Hesabınızı
ve sunucudaki tüm kayıtlarınızı (öğrenciler, dersler, ödemeler, ödevler ve
abonelik kaydı) kalıcı olarak silebilirsiniz.</p>

<h2>Uygulamadan silme</h2>
<ol>
  <li>Ders Defteri'ni açın.</li>
  <li><em>Ayarlar → Eşitleme ve Hesap</em> bölümüne girin.</li>
  <li><em>Hesabı Sil</em>'e dokunup onaylayın.</li>
</ol>
<p>Silme anında yapılır ve geri alınamaz. Cihazınızdaki defter silinmez; onu
uygulamayı kaldırarak temizleyebilirsiniz.</p>

<h2>E-postayla talep</h2>
<p>Uygulamaya erişiminiz yoksa, hesabınızı açarken kullandığınız e-posta
adresinden <a href="mailto:${config.SUPPORT_EMAIL}?subject=Hesap%20silme%20talebi">${config.SUPPORT_EMAIL}</a>
adresine "Hesap silme talebi" konulu bir e-posta gönderin. Kimliğiniz
doğrulandıktan sonra hesabınız ve tüm verileriniz en geç 30 gün içinde silinir.</p>

<h2>Neler silinir, neler kalır?</h2>
<ul>
  <li>Silinir: hesap bilgileriniz ve sunucudaki tüm defter kayıtlarınız.</li>
  <li>Kalmaz: sunucuda hiçbir kopya ya da yedek tutulmaya devam etmez.</li>
  <li>Abonelik: hesabı silmek aboneliği iptal etmez. Aboneliği App Store ya da
  Google Play'den ayrıca iptal etmeniz gerekir.</li>
</ul>
`,
      ),
    );
  });

  // Türkçe adres asıl kabul edilir; İngilizce olan eski bağlantılar için kalır.
  app.get('/gizlilik', async (_request, reply) => reply.redirect('/privacy', 301));
  app.get('/kosullar', async (_request, reply) => reply.redirect('/terms', 301));
}
