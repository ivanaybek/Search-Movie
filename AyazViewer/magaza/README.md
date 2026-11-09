# Google Play mağaza bilgileri

Bu klasörde Play Console'a girmeniz gereken **metinler ve görseller** hazır durur. Metinleri kopyalayıp
yapıştırmanız yeterli. Adım adım yayınlama rehberi için: [`../YAYINLAMA.md`](../YAYINLAMA.md)

## Görseller

| Play Console alanı | Dosya | Ölçü |
|---|---|---|
| Uygulama simgesi | [`simge-512.png`](simge-512.png) | 512 × 512 PNG |
| Öne çıkan görsel | [`tanitim-gorseli-1024x500.png`](tanitim-gorseli-1024x500.png) | 1024 × 500 PNG |
| Telefon ekran görüntüleri | [`ekran-goruntuleri/`](ekran-goruntuleri/) | 1080 × 1920 PNG (9:16) |

Ekran görüntüleri, uygulamanın yayın sürümü emülatörde otomatik test edilirken çekilir ve her başarılı
derlemede bu klasöre eklenir. Önerilen sıra: `1_ana_sayfa`, `2_pdf`, `3_excel`, `4_olustur`, `5_pdf_duzenleme`.
Görüntülerdeki örnek dosyalar `tools/generate_demo_files.py`, simge ve öne çıkan görsel
`tools/generate_store_graphics.py` ile üretilir.

---

## Uygulama adı (en fazla 30 karakter)

```
Ayaz Viewer: PDF ve Excel
```

## Kısa açıklama (en fazla 80 karakter)

```
PDF ve Excel dosyalarını internetsiz aç, düzenle, oluştur ve kolayca paylaş.
```

## Tam açıklama (en fazla 4000 karakter)

```
Ayaz Viewer, PDF ve Excel (XLSX) dosyalarınızı açmanın, düzenlemenin ve oluşturmanın sade ve hızlı yolu. Tamamen çevrimdışı çalışır: internet izni yoktur, reklam yoktur, hesap açmanız gerekmez.

📄 PDF GÖRÜNTÜLEYİCİ
• Çok sayfalı belgelerde akıcı kaydırma
• İki parmakla yakınlaştırma, sürükleme ve çift dokunuşla büyütme
• Sayfa göstergesi ve "Sayfaya git"
• Belgede metin arama (Android 15 ve üzeri)
• Kaldığınız sayfayı hatırlar

📊 EXCEL (XLSX) GÖRÜNTÜLEYİCİ
• Sütun (A, B, C…) ve satır (1, 2, 3…) başlıklarıyla gerçek tablo görünümü
• Birden çok çalışma sayfası ve sayfa sekmeleri
• Sayı, tarih, saat, yüzde ve para birimi biçimleri; Türkçe biçim desteği
• Hücre renkleri, kalın/italik yazı, kenarlıklar, hizalama ve birleşik hücreler
• Dondurulmuş satır ve sütunlar, büyük tablolarda hızlı kaydırma
• Hücrelerde arama (Türkçe karakterlere duyarlı)

✏️ DÜZENLEME
• Excel: hücrelere değer veya formül yazın, satır ve sütun ekleyip silin
• PDF: kalem, vurgulayıcı ve metin notu ile işaretleyin; sayfa döndürün veya silin
• Geri alma desteği
• Her kaydetmede sorar: yeni kopya olarak mı kaydedilsin, asıl dosyanın üzerine mi yazılsın?

➕ OLUŞTUR
• Yeni boş Excel tablosu
• Başlık ve metin yazarak PDF oluşturma (Türkçe karakterlerle)
• Not almak için boş PDF sayfaları

📤 PAYLAŞIM
• Dosyayı tek dokunuşla WhatsApp veya Telegram ile gönderin
• Dosya bağlantı olarak değil, gerçek PDF/XLSX dosyası olarak gider; adı ve uzantısı korunur

📁 DOSYA YÖNETİMİ
• Android dosya seçicisi ve İndirilenler kısayolu
• WhatsApp ve diğer uygulamalardan "Birlikte aç"
• Son açılan dosyalar: ad, boyut ve tarih; ada göre arama
• Sık kullandığınız klasörleri ekleyip içlerine göz atma

🔒 GİZLİLİK
• İnternet izni yok, dosyalarınız hiçbir sunucuya gönderilmez
• Reklam, analitik ve takip yok
• Yalnızca sizin seçtiğiniz dosyalara erişir; geniş depolama izni istemez
• Dosyalarınız siz kaydetmedikçe değiştirilmez

🌙 ARAYÜZ
• Göz yormayan koyu tema (varsayılan) ve açık tema
• Türkçe arayüz ve anlaşılır hata mesajları
• Bozuk veya desteklenmeyen dosyalarda çökmez, nedenini açıklar

Notlar: Parola korumalı dosyalar ve eski .xls (Excel 97-2003) biçimi desteklenmez. Excel formülleri yeniden hesaplanmaz; dosyadaki son hesaplanmış değerler gösterilir.
```

## Diğer alanlar

| Alan | Değer |
|---|---|
| Uygulama kategorisi | **Uygulama → Verimlilik** (Productivity) |
| Etiketler | PDF görüntüleyici, Belge düzenleyici, Elektronik tablo |
| İletişim e-postası | Kendi e-posta adresiniz (Play zorunlu tutar, mağazada görünür) |
| Gizlilik politikası URL'si | `https://github.com/ivanaybek/Search-Movie/blob/main/AyazViewer/GIZLILIK.md` |
| Ücretli / ücretsiz | Ücretsiz |
| Reklam içeriyor mu? | Hayır |
| Varsayılan dil | Türkçe – tr-TR |
