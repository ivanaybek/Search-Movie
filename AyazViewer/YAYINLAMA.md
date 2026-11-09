# Ayaz Viewer'ı Google Play'de yayınlama

Uygulama yayına hazırdır: kod, testler, gizlilik politikası, mağaza metinleri ve görseller bu depoda.
**Sizin yapmanız gereken 3 iş kaldı:**

1. [Google Play geliştirici hesabı açmak](#1-google-play-geliştirici-hesabı) (bir kez, 25 $)
2. [Yükleme anahtarı oluşturup GitHub'a eklemek](#2-yükleme-anahtarı-imza) (bir kez, ~10 dakika)
3. [Play Console'da uygulamayı oluşturup AAB dosyasını yüklemek](#3-play-consoleda-uygulamayı-oluşturma)

> **APK mı, AAB mi?** `AyazViewer-debug.apk` telefona doğrudan kurmak içindir, Play'e **yüklenmez**.
> Google Play yalnızca imzalı **AAB** (Android App Bundle) dosyası kabul eder. AAB'yi GitHub otomatik üretir (adım 2).

---

## 1. Google Play geliştirici hesabı

1. <https://play.google.com/console> adresine Google hesabınızla girin.
2. **Kişisel** hesap türünü seçin, bir kerelik 25 $ ücreti ödeyin ve kimlik doğrulamasını tamamlayın
   (kimlik belgesi ve telefon doğrulaması istenir; onay birkaç gün sürebilir).

> ⚠️ **Yeni kişisel hesaplar için kural:** Uygulamayı herkese açmadan önce **en az 12 kişiyle, 14 gün
> boyunca kapalı test** yapmanız zorunludur. Ailenizden ve arkadaşlarınızdan 12 Gmail adresi toplayın
> (ayrıntısı 3.5. adımda).

## 2. Yükleme anahtarı (imza)

Google Play'e yüklenen her dosya sizin anahtarınızla imzalanmalıdır. Bu anahtarı **bir kez** oluşturursunuz;
GitHub da her derlemede imzalı AAB üretir.

### 2.1 Anahtarı oluşturun (Windows bilgisayarda)

Bilgisayarınızda Java gerekir. En kolayı: <https://adoptium.net> → **Temurin 17 (LTS)** → indirip kurun.
Sonra **PowerShell**'i açın ve şunu yazın (tek satır):

```powershell
keytool -genkeypair -v -keystore ayaz-upload.jks -alias ayaz -keyalg RSA -keysize 2048 -validity 10000 -storetype PKCS12
```

- Bir **parola** sorar: güçlü bir parola belirleyin (en az 8 karakter) ve **bir yere yazın**.
- Ad, kurum, şehir gibi sorulara istediğinizi yazabilirsiniz; sonunda `evet` / `yes` deyin.
- Oluşan `ayaz-upload.jks` dosyasını ve parolayı **güvenli bir yerde yedekleyin** (ör. USB bellek + Google Drive).
  ❗ Bu dosyayı **asla GitHub'a yüklemeyin** ve kimseyle paylaşmayın.

Dosyayı GitHub'a sır olarak ekleyebilmek için metne çevirin (aynı PowerShell penceresinde):

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\ayaz-upload.jks")) | Set-Content ayaz-upload-base64.txt
```

### 2.2 GitHub'a 4 sır (secret) ekleyin

GitHub'da depoyu açın → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
Aşağıdaki 4 sırrı tek tek ekleyin (ad büyük harfle, birebir aynı yazılmalı):

| Name (ad) | Secret (değer) |
|---|---|
| `AYAZ_KEYSTORE_BASE64` | `ayaz-upload-base64.txt` dosyasının **tüm içeriği** (Not Defteri ile açıp kopyalayın) |
| `AYAZ_KEYSTORE_PASSWORD` | 2.1'de belirlediğiniz parola |
| `AYAZ_KEY_ALIAS` | `ayaz` |
| `AYAZ_KEY_PASSWORD` | Aynı parola (PKCS12 anahtar deposunda iki parola aynıdır) |

Sonra `ayaz-upload-base64.txt` dosyasını silebilirsiniz (`ayaz-upload.jks` yedeğiniz kalsın).

### 2.3 İmzalı AAB'yi üretin

1. GitHub'da **Actions** → soldan **Ayaz Viewer** → sağdaki **Run workflow** → **Run workflow**.
2. ~30 dakika sonra tüm adımlar yeşil ✅ olur ve imzalı dosyalar depoya otomatik eklenir:
   - `AyazViewer/yayin/AyazViewer-1.0.0.aab` → **Play Console'a yükleyeceğiniz dosya**
   - `AyazViewer/yayin/AyazViewer-1.0.0-release.apk` → aynı sürümün imzalı APK'sı
3. Dosyayı GitHub'da açıp **Download raw file** (⬇) ile indirin.

> Sırlar eklenmemişse derleme yine başarılı olur, ama imzalı AAB üretilmez (Play'e yüklenemez).

## 3. Play Console'da uygulamayı oluşturma

### 3.1 Uygulama oluştur

**Tüm uygulamalar → Uygulama oluştur**:

| Alan | Değer |
|---|---|
| Uygulama adı | `Ayaz Viewer: PDF ve Excel` |
| Varsayılan dil | Türkçe – tr-TR |
| Uygulama veya oyun | Uygulama |
| Ücretsiz veya ücretli | Ücretsiz |

Beyanları onaylayıp **Uygulama oluştur**'a basın.

### 3.2 "Uygulamanızı ayarlayın" görevleri (Kontrol paneli)

| Görev | Ne seçilecek |
|---|---|
| **Gizlilik politikası** | `https://github.com/ivanaybek/Search-Movie/blob/main/AyazViewer/GIZLILIK.md` |
| **Uygulama erişimi** | "Tüm işlevler herhangi bir özel erişim gerekmeden kullanılabilir" |
| **Reklamlar** | "Hayır, uygulamamda reklam yok" |
| **İçerik derecelendirmesi** | E-posta girin → kategori: **Diğer tüm uygulama türleri** → tüm sorulara **Hayır** → sonuç: 3+ / Herkes |
| **Hedef kitle** | **18 ve üzeri** (yalnızca bu yaş grubunu seçmek, çocuklara yönelik ek kuralları gerektirmez) |
| **Haber uygulaması** | Hayır |
| **Veri güvenliği** | "Uygulamanız kullanıcı verilerini topluyor veya paylaşıyor mu?" → **Hayır** → kaydet. (Uygulama internete hiç bağlanmaz; dosya paylaşımı yalnızca kullanıcının kendi başlattığı WhatsApp/Telegram aktarımıdır.) |
| **Devlet uygulaması** | Hayır |
| **Finansal özellikler** | "Uygulamamda finansal özellik yok" |
| **Sağlık** | Sağlık özelliği yok |

### 3.3 Mağaza girişi

**Büyüme → Mağazadaki varlık → Ana mağaza girişi**: metinleri ve görselleri
[`magaza/README.md`](magaza/README.md) dosyasından kopyalayın:

- Uygulama adı, kısa açıklama, tam açıklama
- Uygulama simgesi: `magaza/simge-512.png`
- Öne çıkan görsel: `magaza/tanitim-gorseli-1024x500.png`
- Telefon ekran görüntüleri: `magaza/ekran-goruntuleri/` içindeki PNG'ler (en az 2, en fazla 8)
- Kategori: **Verimlilik**, iletişim e-postası: kendi adresiniz

### 3.4 İlk sürüm: Play Uygulama İmzalama

İlk AAB yüklenirken Play, **Play Uygulama İmzalama**'yı kabul etmenizi ister. Varsayılan seçenekle
("Google tarafından oluşturulan anahtar") devam edin. Böylece asıl uygulama anahtarını Google saklar; sizin
`ayaz-upload.jks` dosyanız yalnızca yükleme anahtarıdır. Kaybederseniz Play desteğinden sıfırlatabilirsiniz.

### 3.5 Kapalı test (yeni kişisel hesaplar için zorunlu)

1. **Test ve yayınlama → Test → Kapalı test** → bir kanal açın (ör. "Alfa").
2. **Test kullanıcıları**: e-posta listesi oluşturup en az **12 Gmail adresi** ekleyin.
3. **Yeni sürüm oluştur** → `AyazViewer-1.0.0.aab` dosyasını yükleyin.
   Sürüm notu örneği: `İlk sürüm: PDF ve Excel görüntüleme, düzenleme, oluşturma ve WhatsApp/Telegram paylaşımı.`
4. **Sürümü incele → Kullanıma sun**. Google incelemesi genellikle 1–3 gün sürer.
5. Test kullanıcılarına "katılma bağlantısı"nı gönderin; uygulamayı yükleyip **14 gün** boyunca yüklü tutmaları gerekir.

### 3.6 Herkese açık yayın (Üretim)

14 gün dolunca Kontrol panelinde **Üretim erişimi için başvur** çıkar. Birkaç soruyu yanıtlayıp başvurun.
Onaydan sonra **Üretim → Yeni sürüm oluştur** → AAB'yi yükleyin → **Kullanıma sun**. Uygulamanız artık
Google Play'de herkese açıktır. 🎉

---

## Güncelleme yayınlamak

1. `AyazViewer/app/build.gradle.kts` dosyasında iki satırı artırın:
   ```kotlin
   versionCode = 2          // her yüklemede 1 artmalı (Play aynı sayıyı kabul etmez)
   versionName = "1.0.1"    // kullanıcıların gördüğü sürüm
   ```
2. Değişikliği GitHub'a gönderin. Testler geçince `AyazViewer/yayin/AyazViewer-1.0.1.aab` oluşur.
3. Play Console → Üretim → **Yeni sürüm oluştur** → yeni AAB'yi yükleyin.

## Sık sorulan sorular

**Paket adı (`com.ayaz.viewer`) Play'de alınmış derse?**
`AyazViewer/app/build.gradle.kts` içindeki `applicationId = "com.ayaz.viewer"` satırını size özel bir adla
değiştirin (ör. `com.aybek.ayazviewer`). Paket adı yayından sonra bir daha değiştirilemez.

**İnceleme reddedilirse?**
Play Console'daki e-postada nedeni yazar. Genellikle eksik bir form (veri güvenliği, gizlilik politikası) veya
ekran görüntüsüdür. Bu rehberdeki tablolar bu formların tamamını kapsar.

**Debug APK'yı kullananlar Play sürümüne geçebilir mi?**
İmza farklı olduğu için önce telefondaki debug sürümünü kaldırıp Play'den kurmaları gerekir.

**Yayın sürümü test ediliyor mu?**
Evet. GitHub her derlemede uygulamanın R8 ile küçültülmüş yayın koduyla aynı "staging" sürümünü emülatörde
baştan sona kullanır (dosya açma, paylaşım, PDF/Excel oluşturma, düzenleme, kaydetme) ve çökme olup olmadığını
kontrol eder. Ayrıca Google Play'in 16 KB bellek sayfası zorunluluğu otomatik doğrulanır.
Ayrıntılar: [`TEST_RAPORU.md`](TEST_RAPORU.md).
