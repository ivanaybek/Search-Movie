# Ayaz Viewer R8 kuralları.
# Uygulama yansıma (reflection) kullanmaz; Compose ve AndroidX kendi kurallarını taşır.
# XmlPullParser Android çatısında bulunur.
-dontwarn org.xmlpull.v1.**
-dontwarn org.kxml2.**

# PDFBox-Android: isteğe bağlı bağımlılıklar (JPEG2000, BouncyCastle) pakete dahil değildir.
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.**
