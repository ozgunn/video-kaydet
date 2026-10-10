# Video Kaydet

Instagram ve X (Twitter) üzerinden bir gönderi paylaştığında videoyu telefona indiren Android uygulaması.

Paylaşımda gönderi bağlantısını alır, videonun doğrudan adresine çevirir ve **İndirilenler** klasörüne kaydeder.

```
Instagram / X  ──paylaş──▶  Video Kaydet  ──▶  /sdcard/Download/video_….mp4
   (gönderi linki)          (bağlantıyı çözer)
```

## Gereksinimler

- Android 10 (API 29) ve üzeri
- Tek izin: `INTERNET`

## Kurulum

Depodaki hazır APK'yı telefona aktarıp kurabilirsin (`VideoKaydet.apk`), ya da kaynaktan derleyebilirsin:

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

İndirilenen dosya her zaman standart **İndirilenler** klasörüne gider.

## Kullanım

1. Instagram'da bir reel veya X'te bir video gönderisi aç
2. **Paylaş** → **Video Kaydet**
3. Video İndirilenler'e iner

### Instagram oturumu

X gönderileri oturum gerektirmez. Instagram gönderileri için bir kez giriş yapman gerekir:

Uygulamayı aç → **Instagram'da giriş yap** → Instagram'ın giriş ekranı uygulama içinde açılır → giriş yap → **Giriş tamam**.

Uygulama oturum çerezini WebView'in kendi deposundan okuyup kendi özel alanında saklar. Hesap bilgilerin uygulamanın dışına çıkmaz. **Oturumu test et** butonu çerezin geçerli olup olmadığını kontrol eder.

Oturum çerezinin ömrü sınırlıdır; Instagram'dan çıkış yaptığında veya bir süre sonra yeniden giriş yapman gerekebilir.

## Nasıl çalışıyor

| Kaynak | Yöntem |
|---|---|
| X (Twitter) | Tweet kimliği, `id / 1e15 × π` imzasıyla `cdn.syndication.twimg.com` ucuna gider; dönen JSON'daki `video.variants` içinden en yüksek çözünürlüklü mp4 seçilir |
| Instagram | Gönderi sayfası oturum çereziyle çekilir; içindeki `og:video` / `video_versions` / `video_url` alanlarından video adresi alınır |

İki kaynak da işe yaramazsa uygulama nedenini bir **toast** ile söyler ve ayrıntıyı `İndirilenler/videokaydet-hata.txt` dosyasına yazar. Toast kaybolduğu için dosyadan bakabilirsin.

Dosya `MediaStore.Downloads` üzerinden yazılır; önce `IS_PENDING` ile gizlenip, baytlar tamamen indikten sonra görünür hâle getirilir. Yarım kalan bir indirme arka planda bırakılmaz.

## Bilinen sınırlar

- **Instagram Story'leri desteklenmiyor.** Story bağlantıları gönderi sayfası yöntemiyle çözülemiyor.
- **Instagram tarafı kırılgan.** Instagram girişsiz erişimi kapatıyor; video adresi sayfa yapısına gömülü olarak geliyor. Instagram bunu değiştirirse bu yol çalışmaz.
- **Çoklu gönderi** (aynı anda birden fazla video) destekleniyor; aynı isimli dosyalar `-1`, `-2` ekleriyle ayrılıyor.

## Proje yapısı

```
app/src/main/java/app/videosave/
├── SaveActivity.kt   Paylaşım alıcısı: bağlantıyı çözer, videoyu indirir
├── Resolver.kt       Gönderi bağlantısı → doğrudan video adresi
├── LoginActivity.kt  Instagram giriş ekranı (WebView), çerezi okur
├── MainActivity.kt   Kullanım notu ve oturum durumu
└── ErrorLog.kt       Hata günlüğünü İndirilenler'e yazar
```

## Gizlilik

Uygulama hiçbir sunucuya veri göndermez; çözümleme Instagram ve X'in kendi sunucularından yapılır. Oturum çerezi yalnızca cihazda, uygulamanın özel deposunda tutulur.