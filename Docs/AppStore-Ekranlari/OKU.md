# App Store Ekran Görüntüleri

Simülatörden çekildi, App Store Connect'in istediği tam piksel ölçülerinde.
Doğrudan yükleyebilirsin, yeniden boyutlandırma gerekmez.

## iPhone-6.9/ — 1320 × 2868 px

Zorunlu boyut sınıfı. Bu altı görsel yüklenince Apple daha küçük iPhone
ekranları için kendisi ölçekler, ayrıca 6.5" seti hazırlamana gerek yok.

| Dosya | Ekran |
|---|---|
| `01-ozet.png` | Ana sayfa — günün dersleri, kazanç ve bekleyen alacak |
| `02-ogrenciler.png` | Öğrenci listesi — ücret, ders sayısı, bakiye |
| `03-program.png` | Haftalık program |
| `04-odemeler.png` | Ödemeler — bakiyeler ve tahsilat |
| `05-odevler.png` | Ödevler |
| `06-ogrenci-detay.png` | Öğrenci profili — istatistik, iletişim, ders geçmişi |

Sıralama önemli: App Store'da ilk iki görsel liste sayfasında görünür, çoğu
kullanıcı sadece onlara bakar. `01` ve `02` bilerek en dolu ekranlar seçildi.

## iPad-13/ — 2064 × 2752 px

| Dosya | Ekran |
|---|---|
| `01-ozet.png` | Ana sayfa |

**Bu set eksik.** Uygulama `TARGETED_DEVICE_FAMILY = "1,2"` ile iPad'i de
hedeflediği için App Store iPad görselleri istiyor. Apple en az bir görsel
kabul ediyor, yani bununla gönderim yapabilirsin; ama 3-4 görsel daha eklemek
listelemeyi belirgin şekilde güçlendirir.

İki seçeneğin var:

1. **iPad desteğini kaldır.** Uygulama telefon için tasarlandı; iPad'de
   çalışıyor ama geniş ekranda kartlar boş duruyor. Xcode'da hedefi sadece
   iPhone yaparsan bu klasör tamamen gereksizleşir ve Apple iPad'de inceleme
   yapmaz.
2. **iPad setini tamamla.** Simülatörde uygulamayı aç, sekmeler arasında
   gezinip `Cmd+S` ile kaydet.

## Nasıl çekildi

- Açık tema (uygulamanın kağıt/defter kimliği açık temada görünüyor)
- Durum çubuğu sabitlendi: 19:41, tam pil ve sinyal
- Örnek veri: 5 öğrenci, 30 ders, ödemeler ve ödevler — ekranlar boş
  görünmesin diye
- Örnek veri yalnızca çekim derlemesinde vardı; projede kalıntısı yok

## Eksik olan: Pro ekranı

Paywall görüntüsü bilerek eklenmedi. `simctl` ile başlatılan uygulamada
StoreKit test yapılandırması devreye girmediği için ekranda fiyat yerine
"Planlar yüklenemedi" yazıyordu.

Şemaya `One.storekit` bağlantısını ekledim, artık Xcode'dan **Cmd+R** ile
çalıştırdığında fiyatlar görünür. Pro ekranını o zaman `Cmd+S` ile kaydet.

Bu görsel App Store pazarlama seti için şart değil — asıl gereken yer
**abonelik ürünlerinin "App Review Screenshot" alanı**. Orası zorunlu ve
ürünler "Gönderilmeye Hazır" olmadan build incelemeye gitmez.
