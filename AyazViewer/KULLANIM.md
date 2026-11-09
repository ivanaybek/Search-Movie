# Ayaz Viewer – Kullanım Kılavuzu

[← Ana sayfa](README.md) • [Yayınlama](YAYINLAMA.md) • [Gizlilik](GIZLILIK.md) • [Test raporu](TEST_RAPORU.md)

## Hızlı başlangıç

| Ne yapmak istiyorsunuz? | Nasıl? |
|---|---|
| Dosya açmak | Ana sayfada büyük **Dosya Aç** düğmesi (veya PDF aç / Excel aç / İndirilenler kısayolları) |
| WhatsApp'tan gelen dosyayı açmak | WhatsApp'ta dosyaya dokunun → **Birlikte aç** → **Ayaz Viewer** |
| Düzenlemek | Görüntüleyicide üstteki **kalem** simgesi → değişiklik → **Kaydet** (yeni kopya / üzerine yaz sorulur) |
| Yeni PDF veya Excel oluşturmak | Ana sayfa → **Oluştur** |
| Telefona kaydetmek | Görüntüleyicide **⋮** → **Telefona kaydet** |
| WhatsApp / Telegram ile göndermek | Görüntüleyicide **paylaş** simgesi veya listede **⋮ → Paylaş** |
| Tema, sayı biçimi, depolama | Alt menüde **Ayarlar** |

## Özellikler

**Arayüz**
- Varsayılan koyu tema (gerçek siyaha yakın zemin), açık tema ve “sistemi izle” seçeneği
- Mavi–mor vurgu renkleri, yuvarlatılmış kartlar, hafif animasyonlar, özgün dosya türü simgeleri
- Ana sayfada büyük **Dosya Aç** düğmesi, PDF/Excel kısayolları, İndirilenler kısayolu
- Türkçe arayüz ve Türkçe hata mesajları

**PDF görüntüleyici**
- Çok sayfalı belgeler, dikey kaydırma, iki parmakla yakınlaştırma (1×–5×), yakınlaştırılmışken sürükleme ve savurma, çift dokunuşla yakınlaştırma
- Sayfa göstergesi (“3 / 120”), sayfaya git
- Son görüntülenen sayfayı hatırlama (Ayarlar’dan kapatılabilir)
- Belgede metin arama ve eşleşmeleri vurgulama (bkz. sınırlamalar)
- Performans: yalnızca görünen sayfalar çizilir; bitmap’ler bellek sınırlı LRU önbellekte tutulur
  (cihaz bellek sınıfının ⅓’ü, en fazla 128 MB); tek sayfa en fazla ~8 megapiksel; yakınlaştırma bittikten sonra netlik için yeniden çizilir

**Excel (XLSX) görüntüleyici**
- A, B, C… sütun ve 1, 2, 3… satır başlıkları, yatay/dikey kaydırma, yakınlaştırma
- Birden çok çalışma sayfası; alt kısımda sayfa sekmeleri (gizli sayfalar “(gizli)” olarak gösterilir)
- Metin, sayı, tarih, saat, yüzde, para birimi, bilimsel gösterim ve kesir biçimleri
- Hücre dolgu rengi, yazı rengi, kalın/italik/altı çizili/üstü çizili, hizalama, metin kaydırma, kenarlıklar, tema renkleri
- Birleşik hücreler, boş hücreler, dondurulmuş satır/sütunlar, gizli satır/sütunlar, sütun genişlikleri ve satır yükseklikleri
- Uzun metinlerin Excel’deki gibi boş komşu hücrelere taşması; sığmayan sayılar için `####`
- Hücre arama (Türkçe büyük/küçük harf duyarsız: “ığdır” → “Iğdır”), sonuçlar arasında gezinme
- Dokunulan hücrenin adı ve değeri üst çubukta gösterilir, kopyalanabilir
- ⋮ menüsünde **Telefona kaydet**: dosyanın bir kopyasını istediğiniz klasöre kaydeder (PDF'te de aynı)
- Formüller yeniden hesaplanmaz; dosyadaki son hesaplanmış (önbellek) değerler gösterilir
- Büyük tablolar: yalnızca ekrandaki hücreler çizilir; sayfa başına en fazla 3 milyon dolu hücre okunur (aşılırsa uyarı ile kesilir)

**Dosya yönetimi**
- Sistem dosya seçicisi ile açma (İndirilenler klasöründen doğrudan başlatma dahil)
- WhatsApp ve diğer uygulamalardan **Birlikte aç** (VIEW) ve **Paylaş → Ayaz Viewer** (SEND)
- Son açılanlar: ad, boyut, son açılma tarihi; ada göre arama, PDF/Excel filtresi; üç nokta menüsünde Aç / Paylaş / Listeden kaldır
- **Dosyalar** sekmesi: seçtiğiniz klasörleri (ör. `Download/WhatsApp Documents`) ekleyip içindeki PDF/XLSX dosyalarına göz atma ve arama
- Kalıcı erişim izni verilemeyen dosyalar (ör. WhatsApp’tan gelenler) uygulamanın özel alanına birebir kopyalanır; böylece “Son Açılanlar”dan tekrar açılabilir. Bu kopyalar Ayarlar’dan temizlenebilir
- Kullanıcının dosyaları hiçbir zaman değiştirilmez veya silinmez; “Listeden kaldır” yalnızca listeyi ve uygulamanın kendi kopyasını etkiler
- Bozuk, boş, şifreli veya desteklenmeyen dosyalarda uygulama çökmez; Türkçe açıklama gösterilir

**Düzenleme**
- Görüntüleyicinin üst çubuğunda **Düzenle** (kalem) simgesi. Düzenleme kipinde: **Geri al**, **Kaydet**, **Bitti**.
- **Excel:** hücreye dokunun, üstteki kutuya değer yazıp ✓'ye basın (sonra alttaki hücreye geçer). Sayılar Türkçe
  yazılır (`1.234,56`, `12,5%`); tarih biçimli hücrelere `05.03.2024`; formüller İngilizce yazılır (`=SUM(A1:A5)`);
  başına `'` konan değer metin olarak kalır. Satır/sütun menüsü: üstüne/altına satır ekle, satır sil, soluna/sağına
  sütun ekle, sütun sil, hücreyi temizle. Ekleme/silmede formüller, birleşik hücreler, sütun genişlikleri, koşullu
  biçimler ve veri doğrulamaları birlikte kayar; silinen hücreye başvuran formül Excel'deki gibi `#REF!` olur.
- **PDF:** Kalem, vurgulayıcı, metin notu (5 renk, 3 kalınlık), silgi (henüz kaydedilmemiş işaretler için), sayfayı
  döndür, sayfayı sil. Tek parmakla çizilir; iki parmakla kaydırılır/yakınlaştırılır. İşaretlemeler kaydedilince sayfanın
  içine çizilir. (Android'in yerleşik PDF bileşeni PDF notlarını göstermediği için not olarak değil, sayfa içeriği
  olarak eklenir; böylece Ayaz Viewer dahil her uygulamada aynı görünür.) Belgenin kendi metni değişmez, aranabilir ve
  seçilebilir kalır; eklenen metin notları da aranabilir.
- **Kaydetme her seferinde sorulur:** *Yeni kopya olarak kaydet* (sistem kaydetme ekranıyla istediğiniz klasöre,
  "Ad (düzenlendi).xlsx" önerisiyle) veya *Asıl dosyanın üzerine yaz* (yalnızca yazma izni varsa; geri alınamaz
  uyarısı gösterilir). Kaydetmeden çıkmaya çalışırsanız uyarı çıkar. Kaydedene kadar asıl dosyaya dokunulmaz.

**Oluştur**
- Ana sayfadaki **Oluştur** kartı: *Yeni Excel tablosu* (boş tablo, doğrudan düzenleme kipinde), *Yeni PDF (metin
  yaz)* (başlık + metin yazılır, satırlar kaydırılarak sayfalara bölünür), *Boş PDF* (1/3/5 sayfa; kalem ve not için).
- Oluşturulan belgeler uygulamada saklanır ve Son Açılanlar'da görünür. Görüntüleyicinin ⋮ menüsündeki
  **Telefona kaydet** ile istediğiniz klasöre (ör. İndirilenler) kaydedilir; WhatsApp/Telegram ile paylaşılabilir.
  Bu belgeleri listeden kaldırmak onları sildiği için onay istenir. "Listeyi temizle" bu belgeleri silmez.

**WhatsApp / Telegram ile paylaşım**
- Görüntüleyicinin sağ üstünde paylaş simgesi; son açılanlarda üç nokta → **Paylaş**
- Uygulamanın kendi paylaşım penceresi; **yalnızca iki seçenek**: WhatsApp ve Telegram. Sistem paylaşım menüsü açılmaz
- WhatsApp ve WhatsApp Business desteklenir; ikisi de yüklüyse hangisinin kullanılacağı sorulur (Telegram, Telegram X vb. için de aynı)
- Dosya bağlantı olarak değil, gerçek PDF/XLSX dosyası olarak (`FileProvider` content URI + geçici okuma izni) gönderilir
- Dosya adı ve uzantısı korunur; içerik bayt bayt aynıdır
- Uygulama yüklü değilse Türkçe uyarı gösterilir; paylaşımdan vazgeçmek hata oluşturmaz

**Gizlilik**
- `INTERNET` izni yoktur (CI her derlemede bunu doğrular); geniş depolama izni yoktur
- Reklam, analitik, takip, hesap yoktur; dosyalar hiçbir sunucuya gönderilmez
- Dosya içerikleri loglanmaz; uygulama verisi buluta yedeklenmez (`allowBackup=false`)

---

## Bilinen sınırlamalar

- **PDF metin araması** Android 15+ (veya Google Play sistem güncellemesiyle güncel PDF bileşeni olan) cihazlarda çalışır. Android 13/14’te bileşen eskiyse arama yerine Türkçe bilgi mesajı gösterilir. Taranmış (görüntü) PDF’lerde metin katmanı olmadığından arama sonuç vermez.
- **Parola korumalı PDF** ve **parola korumalı XLSX** dosyaları açılamaz (açıklayıcı mesaj gösterilir).
- **Eski .xls (Excel 97–2003)** biçimi desteklenmez; dosyayı .xlsx olarak kaydetmek gerekir.
- **PDF'te var olan metin değiştirilemez** (PDF bir baskı düzeni biçimidir); üzerine yazma/işaretleme, sayfa döndürme
  ve silme yapılabilir. Silgi yalnızca henüz kaydedilmemiş işaretlemeleri siler; kaydedilen işaretlemeler sayfanın
  parçası olur ve sonradan silinemez (kaydetmeden önce "Geri al" kullanın).
- Başka uygulamalarda eklenmiş **PDF notları/vurguları ve form alanları** Ayaz Viewer'da görünmez (Android'in yerleşik
  PDF bileşeni bunları çizmez); belge içeriğinin kendisi eksiksiz gösterilir. Sayfa döndürme/silme sonrası "Geri al" ile geri dönülebilir.
- **Excel düzenlemede** formüller yeniden hesaplanmaz (Excel'de açılınca hesaplanır), formül yazımı İngilizcedir.
  Hücre biçimi (renk, kalın vb.) değiştirilemez, değer yazılan hücrenin mevcut biçimi korunur. Excel *tablosu*
  (Tablo olarak biçimlendir) içeren sayfalarda satır/sütun ekleme-silme kapalıdır; başka sayfalardaki formüller ve
  adlandırılmış aralıklar kaydırılmaz; resim ve grafikler yerinde kalır. Paylaşılan formüller ilk düzenlemede tek
  tek formüllere açılır (sonuç aynıdır).
- **Yazma izni:** "Üzerine yaz" yalnızca dosya Dosya Aç / klasör ile yazma izniyle açıldıysa veya uygulamanın kendi
  kopyasıysa (ör. WhatsApp'tan açılan) mümkündür. WhatsApp'tan açılan dosyada üzerine yazmak WhatsApp'taki dosyayı
  değil, uygulamadaki kopyayı değiştirir; değiştirilmiş hali "Yeni kopya" veya Paylaş ile gönderebilirsiniz.
- XLSX’te **grafikler, resimler, şekiller, koşullu biçimlendirme, açıklamalar ve köprüler** gösterilmez; grafik sayfaları “görüntülenemiyor” mesajıyla listelenir.
- Formüller **yeniden hesaplanmaz**; dosyayı kaydeden programın sakladığı son değer gösterilir. Bazı araçlar (ör. kod ile üretilen dosyalar) önbellek değeri yazmazsa formül hücresi boş görünür.
- Excel yazı tipleri yerine Android’in sistem yazı tipi kullanılır; sütun genişlikleri Excel ölçüsüne göre yaklaşık hesaplanır.
- Zengin metin hücrelerinde (bir hücrede birden çok biçim) tek bir biçim uygulanır.
- Dosyalar sekmesi yalnızca sizin eklediğiniz klasörleri listeler (geniş depolama izni bilinçli olarak istenmez). Android 11+ “Download” kök klasörünün tamamına klasör izni vermeye izin vermez; alt klasör (ör. `Download/WhatsApp Documents`) eklenebilir veya “İndirilenler” kısayoluyla seçici kullanılabilir.
- Arayüz metinleri Türkçe olarak kaynak koda yazılmıştır (yalnızca Türkçe kullanım için).
