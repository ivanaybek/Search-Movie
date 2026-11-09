# Ayaz Viewer – Geliştirici Notları

[← Ana sayfa](README.md) • [Kullanım](KULLANIM.md) • [Yayınlama](YAYINLAMA.md) • [Test raporu](TEST_RAPORU.md)

- Paket adı: `com.ayaz.viewer` • Sürüm: 1.0.0 (versionCode 1)
- Android 8.0+ (minSdk 26); hedef ve derleme: Android 16 (targetSdk/compileSdk 36)
- Derleme ve testler GitHub Actions'ta otomatik çalışır: `.github/workflows/ayaz-viewer.yml`

## Kullanılan teknolojiler

| Alan | Seçim | Neden |
|---|---|---|
| Dil / UI | Kotlin 2.1, Jetpack Compose, Material 3 | Native, modern, akıcı arayüz |
| Derleme | Gradle 8.14.3 (wrapper), Android Gradle Plugin 8.10.1 | Standart Android Studio projesi |
| PDF | Android `PdfRenderer` (sistem bileşeni) | Ek SDK gerektirmez, çevrimdışı |
| PDF arama | `PdfRenderer.Page.searchText()` | Android 15+ (veya güncel sistem PDF bileşeni) |
| PDF düzenleme/oluşturma | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) 2.0.27 (Apache 2.0) | Çevrimdışı; işaretlemeleri sayfaya çizer, sayfa döndürür/siler, metinden PDF üretir |
| Yazı tipi | DejaVu Sans (Bitstream Vera lisansı, `assets/fonts`) | PDF'e Türkçe karakterli metin yazmak için |
| XLSX | Projeye özel `:xlsx` modülü (saf Kotlin, ZIP + akışlı XML) | Apache POI gibi ağır kitaplıklar yerine hızlı ve hafif |
| Dosya erişimi | Storage Access Framework (SAF), `FileProvider` | Geniş depolama izni gerekmez |
| Veri saklama | Uygulama içi JSON (`AtomicFile`), `SharedPreferences` | Ek veritabanı kitaplığı yok |

Reklam, analitik veya ağ kullanan hiçbir SDK yoktur. AndroidX/Compose ve Kotlin coroutines dışında tek kitaplık,
PDF düzenleme için gereken açık kaynak PdfBox-Android'dir (internet izni yoktur; CI her derlemede doğrular).

### Proje yapısı

```
AyazViewer/
├── app/                         Android uygulaması
│   └── src/main/java/com/ayaz/viewer/
│       ├── MainActivity.kt      Ana ekran (alt menü: Ana Sayfa, Son Açılanlar, Dosyalar, Ayarlar)
│       ├── viewer/              Görüntüleyici etkinliği
│       │   ├── pdf/             PdfController (tembel çizim + LRU önbellek), PdfViewerScreen
│       │   └── xlsx/            XlsxController, SheetGrid (Canvas tablo), XlsxViewerScreen
│       ├── data/                Son açılanlar, ayarlar, SAF erişimi, klasörler, dosya açıcı
│       ├── share/               WhatsApp/Telegram paylaşım penceresi ve mantığı
│       └── ui/                  Tema, simgeler, ekranlar ve ortak bileşenler
├── xlsx/                        Bağımsız XLSX okuma/düzenleme motoru (JVM'de test edilebilir)
├── smoketest/                   Yayın sürümü uçtan uca testi (UiAutomator) + mağaza ekran görüntüleri
├── magaza/                      Google Play metinleri, simge, öne çıkan görsel, ekran görüntüleri
├── yayin/                       İmzalı AAB/APK (imzalama sırları tanımlıysa CI üretir)
├── tools/                       Örnek dosya ve mağaza görseli üreticileri
└── samples/                     Telefonda elle denemek için örnek dosyalar
```

---

## Derleme
1. Android Studio (Ladybug veya daha yeni) → **Open** → `AyazViewer` klasörü.
2. Gradle eşitlemesi bitince: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
3. Çıktı: `app/build/outputs/apk/debug/app-debug.apk`

Komut satırından (JDK 17 ve Android SDK kurulu olmalı):
```bash
cd AyazViewer
./gradlew :app:assembleDebug          # debug APK
./gradlew :xlsx:test :app:testDebugUnitTest   # birim testleri
./gradlew :app:connectedDebugAndroidTest      # bağlı cihaz/emülatörde testler
./gradlew :smoketest:connectedStagingAndroidTest  # yayın (R8) sürümü uçtan uca testi
./gradlew :app:bundleRelease                  # Google Play için AAB
```

## İmzalı yayın sürümü (yerelde)
1. Anahtar deposu oluşturun (bir kez; dosyayı ve parolaları güvenli saklayın, depoya eklemeyin):
   ```bash
   keytool -genkeypair -v -keystore ~/ayaz-release.jks -alias ayaz -keyalg RSA -keysize 4096 -validity 10000
   ```
2. `AyazViewer/keystore.properties` dosyası oluşturun (`.gitignore` içindedir):
   ```properties
   AYAZ_KEYSTORE_FILE=/home/kullanici/ayaz-release.jks
   AYAZ_KEYSTORE_PASSWORD=depo_parolasi
   AYAZ_KEY_ALIAS=ayaz
   AYAZ_KEY_PASSWORD=anahtar_parolasi
   ```
   (Aynı değerler ortam değişkeni olarak da verilebilir.)
3. Derleyin: `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
   (R8 ile küçültülmüş ve imzalı).
4. Doğrulayın: `apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk`

Android Studio ile: **Build → Generate Signed Bundle / APK → APK** → anahtar deposunu seçin → *release*.

> Debug sürümden release sürüme geçerken imza farklı olduğu için önce eski uygulamayı kaldırmanız gerekir.

---

## Testler

Testlerin tamamı depoda bulunur ve GitHub Actions’ta otomatik çalışır:

| Test grubu | Nerede | Kapsam |
|---|---|---|
| `xlsx/src/test` (JVM, 42 test) | `./gradlew :xlsx:test` | Hücre referansları, 60+ sayı/tarih biçimi senaryosu, basit/çok sayfalı/birleşik/el yazımı XLSX, Türkçe karakterler, 1904 tarih sistemi, bozuk/boş/eski .xls/ZIP-olmayan dosyalar, 100.000 × 12 büyük sayfa, hücre sınırı, iptal, Türkçe arama |
| `smoketest` (UiAutomator, yayın sürümü; emülatör: Android 15) | `:smoketest:connectedStagingAndroidTest` | R8 ile küçültülmüş sürümde: başka uygulamadan PDF/Excel açma, son dosyalar, paylaşım penceresi, metinden PDF oluşturma, PDF'e çizim + kaydetme, yeni Excel + hücre yazma + kaydetme, ayarlar ve lisanslar; çökme/ANR kontrolü; mağaza ekran görüntüleri |
| `app/src/test` (JVM, 6 test) | `./gradlew :app:testDebugUnitTest` | Dosya türü tespiti (içerik imzası), Türkçe ad temizleme, uzantı, boyut/tarih biçimleri |
| `app/src/androidTest` (44 test; emülatör: Android 13 ve 15) | `connectedDebugAndroidTest` | Tek/çok sayfalı/400 sayfalık/karışık boyutlu PDF çizimi ve süreleri, şifreli/bozuk/boş PDF, Türkçe dosya adı, PDF metin araması (Android 15), XLSX’in Android XML ayrıştırıcısı ile okunması, büyük XLSX, son açılanların yeniden başlatmada korunması, “Birlikte aç” (VIEW) ve “Paylaş” (SEND) niyetleri, paylaşım dosyasının adı/boyutu/içeriği, WhatsApp/Telegram yüklü değil uyarısı, alt menü gezinmesi, koyu/açık tema, ekran döndürme, sayfaya git ve son sayfayı hatırlama, sayfa sekmeleri, hücre araması |

Test dosyaları `tools/generate_samples.py` ile üretilir; `samples/` klasöründeki dosyaları telefona kopyalayarak elle de deneyebilirsiniz.

Gerçek test sonuçları ve hangi testlerin nerede çalıştığı için `TEST_RAPORU.md` dosyasına bakın.
