import type { FastifyInstance } from 'fastify';
import { config } from '../config.js';

/**
 * Tanıtım ve destek sayfaları.
 *
 * App Store Connect hem Marketing URL hem Support URL ister ve ikisi de
 * incelemede açılıp kontrol edilir. Üçüncü bir servise (Notion, Carrd vb.)
 * bağımlı kalmamak için aynı sunucudan servis edilirler; API ile aynı yerde
 * durmaları bakım yükünü de tek noktada tutar.
 */

const APP_NAME = 'Ders Defteri';

function layout(title: string, body: string, opts: { wide?: boolean } = {}): string {
  return `<!doctype html>
<html lang="tr">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${title}</title>
<meta name="description" content="Özel ders veren öğretmenler için öğrenci, program, ödeme ve ödev defteri.">
<meta property="og:title" content="${title}">
<meta property="og:description" content="Özel ders veren öğretmenler için öğrenci, program, ödeme ve ödev defteri.">
<meta property="og:type" content="website">
<style>
  :root {
    --paper:#F7F2E7; --card:#FFFFFF; --ink:#26303E; --soft:#6B7280;
    --board:#1E4B39; --board-dark:#143528; --line:rgba(38,48,62,.10);
    --amber:#DF9E3B;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --paper:#16150F; --card:#232119; --ink:#ECE9E2; --soft:#9C988D;
      --board:#2F6C51; --board-dark:#224E3B; --line:rgba(236,233,226,.12);
    }
  }
  * { box-sizing:border-box; }
  body {
    margin:0; background:var(--paper); color:var(--ink);
    font:16px/1.65 -apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,sans-serif;
  }
  .wrap { max-width:${opts.wide ? '1080px' : '46rem'}; margin:0 auto; padding:0 20px 72px; }
  header.top { padding:22px 0; }
  .brand { display:flex; align-items:center; gap:10px; font-weight:700; letter-spacing:-.01em; }
  .brand .mark {
    width:30px; height:30px; border-radius:8px; flex:none;
    background:linear-gradient(140deg,var(--board),var(--board-dark));
    display:grid; place-items:center; color:#fff; font-size:15px;
  }
  .hero { padding:26px 0 8px; }
  h1 { font-size:clamp(2rem,5vw,3rem); line-height:1.12; margin:.2em 0 .3em; letter-spacing:-.02em; }
  .lede { font-size:1.15rem; color:var(--soft); max-width:34rem; }
  h2 { font-size:1.35rem; margin:2.4rem 0 .6rem; letter-spacing:-.01em; }
  h3 { font-size:1.02rem; margin:1.5rem 0 .3rem; }
  a { color:var(--board); }
  @media (prefers-color-scheme: dark) { a { color:#8FCBAA; } }
  .shots {
    display:grid; grid-template-columns:repeat(auto-fit,minmax(190px,1fr));
    gap:18px; margin:34px 0 8px;
  }
  .shots figure { margin:0; }
  .shots img {
    width:100%; height:auto; border-radius:16px; display:block;
    border:1px solid var(--line); box-shadow:0 8px 26px rgba(0,0,0,.10);
  }
  .shots figcaption { font-size:.82rem; color:var(--soft); margin-top:8px; text-align:center; }
  .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); gap:14px; margin-top:12px; }
  .card {
    background:var(--card); border:1px solid var(--line); border-radius:14px; padding:16px 18px;
  }
  .card h3 { margin:0 0 4px; font-size:1rem; }
  .card p { margin:0; color:var(--soft); font-size:.92rem; }
  .board {
    background:linear-gradient(140deg,var(--board),var(--board-dark));
    color:#fff; border-radius:18px; padding:22px 24px; margin:30px 0;
  }
  .board p { color:rgba(255,255,255,.88); margin:.4em 0 0; }
  .board strong { color:#fff; }
  ul { padding-left:1.15rem; } li { margin:.3rem 0; }
  .faq { border-top:1px solid var(--line); padding-top:6px; }
  .muted { color:var(--soft); font-size:.9rem; }
  footer {
    border-top:1px solid var(--line); margin-top:52px; padding-top:20px;
    display:flex; flex-wrap:wrap; gap:14px; align-items:center;
    font-size:.88rem; color:var(--soft);
  }
  footer nav { display:flex; gap:14px; flex-wrap:wrap; margin-left:auto; }
  code { background:var(--card); border:1px solid var(--line); padding:1px 6px; border-radius:6px; font-size:.9em; }
</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <a href="/" style="text-decoration:none;color:inherit">
      <div class="brand"><span class="mark">✦</span> ${APP_NAME}</div>
    </a>
  </header>
  ${body}
  <footer>
    <span>© ${new Date().getFullYear()} Adil Emre Tuna</span>
    <nav>
      <a href="/">Ana sayfa</a>
      <a href="/destek">Destek</a>
      <a href="/gizlilik">Gizlilik</a>
      <a href="/kosullar">Koşullar</a>
    </nav>
  </footer>
</div>
</body>
</html>`;
}

export async function siteRoutes(app: FastifyInstance) {
  app.get('/', async (_request, reply) => {
    reply.type('text/html; charset=utf-8').send(
      layout(
        `${APP_NAME} — özel ders defteri`,
        `
<section class="hero">
  <h1>Özel ders veren öğretmenin defteri.</h1>
  <p class="lede">Öğrencilerin, ders programın, ödemeler ve ödevler tek yerde.
  Kim ne zaman gelecek, kim ne kadar ödedi — hepsi bir bakışta.</p>
</section>

<div class="shots">
  <figure><img src="/img/ozet.png" alt="Ana sayfa: günün dersleri ve kazanç özeti" loading="lazy"><figcaption>Günün özeti</figcaption></figure>
  <figure><img src="/img/ogrenciler.png" alt="Öğrenci listesi" loading="lazy"><figcaption>Öğrenciler</figcaption></figure>
  <figure><img src="/img/program.png" alt="Haftalık ders programı" loading="lazy"><figcaption>Haftalık program</figcaption></figure>
  <figure><img src="/img/odemeler.png" alt="Ödemeler ve bakiyeler" loading="lazy"><figcaption>Ödemeler</figcaption></figure>
</div>

<h2>Ne işe yarar?</h2>
<div class="grid">
  <div class="card"><h3>Ders programı</h3><p>Haftalık ve aylık görünüm. Tekrarlayan dersleri bir kez tanımla, kendiliğinden oluşsun.</p></div>
  <div class="card"><h3>Ücret ve bakiye</h3><p>Saatlik ücret ya da derse özel tutar. Kimin borcu var, kim avans ödemiş — otomatik hesaplanır.</p></div>
  <div class="card"><h3>Ödev takibi</h3><p>Verilen ödevler, teslim tarihleri ve gecikenler. Teslim gününden önce hatırlatma gelir.</p></div>
  <div class="card"><h3>Hatırlatmalar</h3><p>Ders saatinden önce bildirim. Pro ile her sabah o günün tüm programı tek bildirimde.</p></div>
  <div class="card"><h3>Veli raporu</h3><p>Dersleri, ödevleri ve bakiyeyi tek PDF'te toplayıp veliye gönder.</p></div>
  <div class="card"><h3>Yedek ve eşitleme</h3><p>Pro ile defterin hesabına yedeklenir; yeni telefonda giriş yapman yeterli.</p></div>
</div>

<div class="board">
  <strong>Ücretsiz başla.</strong>
  <p>2 öğrenciye kadar tüm temel özellikler ücretsiz. Ders Defteri Pro ile sınırsız
  öğrenci, cihazlar arası eşitleme, PDF veli raporu, günlük program özeti ve
  CSV dışa aktarma açılır.</p>
</div>

<h2>Verilerin</h2>
<p class="muted">Kayıtların cihazında tutulur. Pro aboneliğiyle eşitlemeyi açarsan
şifreli bağlantı üzerinden kendi hesabına yedeklenir ve yalnızca sana görünür.
Reklam yok, izleme yok, veri satışı yok. Hesabını ve tüm kayıtlarını
uygulama içinden kalıcı olarak silebilirsin.</p>

<p class="muted">Sorusu olan: <a href="mailto:${config.SUPPORT_EMAIL}">${config.SUPPORT_EMAIL}</a></p>
`,
        { wide: true },
      ),
    );
  });

  app.get('/destek', async (_request, reply) => {
    reply.type('text/html; charset=utf-8').send(
      layout(
        `Destek — ${APP_NAME}`,
        `
<h1 style="font-size:2rem">Destek</h1>
<p class="lede">Takıldığın her konuda yaz, genellikle aynı gün dönüş yapıyorum:
<a href="mailto:${config.SUPPORT_EMAIL}">${config.SUPPORT_EMAIL}</a></p>

<h2>Sık sorulanlar</h2>

<div class="faq">
<h3>Uygulamayı kullanmak için hesap açmam gerekiyor mu?</h3>
<p class="muted">Hayır. Uygulama hesapsız da tam çalışır ve defterin cihazında tutulur.
Hesap yalnızca cihazlar arası eşitleme için gerekir; o da Ders Defteri Pro'ya dahildir.</p>

<h3>Yeni telefona geçtim, kayıtlarım nerede?</h3>
<p class="muted">Eşitlemeyi açtıysan aynı Apple hesabıyla giriş yapman yeterli — kayıtların geri gelir.
Eşitleme kapalıyken defter yalnızca eski cihazda tutulduğu için aktarılamaz.
Yeni cihaza geçmeden önce <em>Ayarlar → Eşitleme</em> bölümünden eşitlemeyi açman önerilir.</p>

<h3>Aboneliğimi nasıl iptal ederim?</h3>
<p class="muted">iPhone'da <em>Ayarlar → (adın) → Abonelikler</em> yolunu izle.
İptal işlemi Apple tarafından yönetilir, uygulamadan yapılamaz.
İptal ettiğinde kayıtların silinmez.</p>

<h3>Aboneliğim göründüğü halde Pro açılmadı.</h3>
<p class="muted">Uygulamada <em>Ayarlar → Ders Defteri Pro → Satın Alımları Geri Yükle</em> düğmesini kullan.
Sorun sürerse App Store hesabının ülkesi ile satın alma yaptığın hesabın aynı olduğundan emin ol.</p>

<h3>Ders ücretini sonradan değiştirirsem eski dersler etkilenir mi?</h3>
<p class="muted">Hayır. Her ders, işlendiği andaki ücretle kilitlenir.
Saatlik ücreti güncellemen geçmiş kayıtları ve hesaplanmış bakiyeyi bozmaz.</p>

<h3>Ödevler ve dersler için bildirim gelmiyor.</h3>
<p class="muted">iPhone <em>Ayarlar → Bildirimler → Ders Defteri</em> altında izin verildiğinden emin ol.
Uygulama içinde <em>Ayarlar</em> bölümünden hatırlatıcıların açık olduğunu da kontrol et.</p>

<h3>Hesabımı ve verilerimi silmek istiyorum.</h3>
<p class="muted">Uygulamada <em>Ayarlar → Eşitleme ve Hesap → Hesabı Sil</em>.
Bu işlem sunucudaki tüm kayıtlarını kalıcı olarak siler ve geri alınamaz.
Cihazındaki defter silinmez; onu uygulamayı kaldırarak temizleyebilirsin.</p>

<h3>Verilerimin yedeğini nasıl alırım?</h3>
<p class="muted">Pro aboneliğiyle <em>Ayarlar → Dışa Aktar</em> bölümünden öğrenci, ders, ödeme
ve ödev kayıtlarını CSV dosyaları olarak alabilirsin. Bu dosyalar Excel ve Numbers ile açılır.</p>
</div>

<h2>Hâlâ çözülmediyse</h2>
<p class="muted">E-postana şunları eklersen çok daha hızlı çözerim: kullandığın iPhone modeli,
iOS sürümü, uygulama sürümü (<em>Ayarlar</em> ekranının en altında yazar) ve sorunun
ekran görüntüsü.</p>
`,
      ),
    );
  });

  // Türkçe adresler asıl kabul edilir; İngilizce olanlar uygulama içindeki
  // eski bağlantılar için çalışmayı sürdürür.
  app.get('/support', async (_request, reply) => reply.redirect('/destek', 301));
}
