# NoDoomScroll

Android cihazlarda kısa video akışlarının (**YouTube Shorts**, **Instagram Reels**, **TikTok**, **Facebook Reels**, **X Shorts**) yarattığı doomscroll döngüsünü kesmek için geliştirilen, native Kotlin ve Jetpack Compose tabanlı hafif bir anti-doomscroll uygulamasıdır.

> Uygulamanın amacı sosyal medya uygulamalarını tamamen yasaklamak değil; yalnızca sonsuz kaydırma (short-form feed) akışlarına girildiğinde anında geri veya ana ekrana yönlendirerek sürtünme oluşturmaktır.

---

## Özellikler (MVP)

- **YouTube Shorts Engelleme (`com.google.android.youtube`):** Shorts oynatıcı bileşenleri ekranda görünür olduğunda `GLOBAL_ACTION_BACK` tetikler. Normal YouTube videolarını ve YouTube Music (`com.google.android.apps.youtube.music`) uygulamasını etkilemez.
- **Instagram Reels Engelleme (`com.instagram.android`):** Reels oynatıcı bileşenlerini veya seçili Reels sekmesini algıladığında `GLOBAL_ACTION_BACK` tetikler. Ana akış, DM'ler ve Threads (`com.instagram.barcelona`) açık kalır.
- **TikTok Engelleme (`com.zhiliaoapp.musically` / `com.ss.android.ugc.trill`):** Uygulama öne geçtiğinde `GLOBAL_ACTION_HOME` ile ana ekrana döndürür.
- **Facebook Reels Engelleme (`com.facebook.katana` / `com.facebook.lite`):** Facebook Reels/Shorts oynatıcısını view ID, Activity sınıfı ve seçili sekme sinyalleriyle algılayıp `GLOBAL_ACTION_BACK` tetikler; normal Haber Akışı gönderilerini ve ana sayfa kartlarını etkilemez.
- **X (Twitter) Shorts Engelleme (`com.twitter.android`):** Tam ekran dikey/immersive video akışını algılayıp `GLOBAL_ACTION_BACK` tetikler; normal zaman akışı gönderileri ve satır içi videolar açık kalır.
- **Anti-Spam Soğuma Koruması (`CooldownGate`):** Platform bazlı `1200ms` cooldown ile arka arkaya tetiklenen geri atma döngülerini önler.
- **Gizlilik Odaklı Erişilebilirlik Servisi:** `accessibility_service_config.xml` yalnızca hedef paketleri dinler; ağ izni veya veri toplama yoktur.
- **Debug Log Modu (Kalibrasyon):** Uygulama içi anahtar açıldığında aktif pencere sınıfını, görünür bileşen `viewId` bilgilerini ve seçili sekme etiketlerini `Logcat` (`NoDoomScroll` etiketi) üzerinden incelemeyi sağlar.

---

## Mimari ve Teknoloji

- **Dil & UI:** Kotlin, Jetpack Compose (Material 3)
- **Hedef SDK'lar:** `minSdk = 26` (Android 8.0+), `compileSdk = 36`, `targetSdk = 36`, AGP `9.0.1`
- **Paket Yapısı (`app/src/main/kotlin/com/nodoomscroll/app/`):**
  - `blocker/` — Android framework'ünden tamamen bağımsız saf Kotlin kural motoru (`BlockRule`, `DoomScrollRuleEngine`, `CooldownGate`, `BlockerModels`)
  - `data/` — `SharedPreferences` tabanlı reaktif + senkron ayar deposu (`BlockerSettings`, `SettingsRepository`)
  - `service/` — `DoomScrollAccessibilityService` ve sistem ayarları yardımcısı (`AccessibilityStatusHelper`)
  - `ui/home/` — Servis durumu, platform anahtarları ve debug modunu yöneten kontrol paneli (`HomeScreen`, `HomeViewModel`)

---

## Kurulum ve Test

### 1. Birim Testlerini Çalıştırma
```bash
./gradlew testDebugUnitTest
```
`blocker/` kural motoru, `1200ms` cooldown mekanizması ve `HomeViewModel` için JVM birim testlerini çalıştırır.

### 2. Debug APK Derleme ve Cihaza Yükleme
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Telefonda Etkinleştirme
1. **NoDoomScroll** uygulamasını açıp **"Erişilebilirlik Ayarlarını Aç"** butonuna dokunun.
2. **Yüklü uygulamalar → NoDoomScroll Koruma Servisi** iznini açın.
   - *Android 13+ / Samsung notu:* Sistem **"Kısıtlanmış ayar"** uyarısı gösterirse **Ayarlar → Uygulamalar → NoDoomScroll → sağ üst `⋮` → Kısıtlanmış ayarlara izin ver** seçeneğini onaylayıp tekrar Erişilebilirlik menüsünden servisi açın.
3. Uygulamaya dönüp durum kartının **"Koruma Servisi Aktif"** olduğunu doğrulayın.

### 4. Otomatik Dağıtım (GitHub Actions + Firebase App Distribution)
[`firebase-app-distribution.yml`](.github/workflows/firebase-app-distribution.yml) iş akışı, `main` dalına her push sonrasında birim testlerini çalıştırıp `app-debug.apk` üretir ve aşağıdaki GitHub Repository Secrets tanımlıysa APK'yı otomatik olarak **Firebase App Distribution** üzerinden test cihazına gönderir:

- `FIREBASE_APP_ID`: Firebase Android uygulama kimliği (`1:...:android:...`)
- `FIREBASE_SERVICE_CREDENTIALS`: Google Cloud Service Account JSON anahtar içeriği
- `FIREBASE_TESTER_EMAIL`: Dağıtım bildirimi gönderilecek test e-posta adresi
- `ANDROID_DEBUG_KEYSTORE_BASE64` *(opsiyonel)*: Lokal USB ve bulut APK imzalarının eşleşmesi için base64 kodlu `debug.keystore`

---

## Yol Haritası

- [x] **Faz 0:** Proje iskeleti ve ilk derleme doğrulaması
- [x] **Faz 1:** Çekirdek kural motoru, `DoomScrollAccessibilityService`, kontrol paneli ve adaptif uygulama logosu
- [ ] **Faz 2:** Gerçek cihaz kalibrasyonu (güncel YouTube/Instagram sürümlerine göre `viewId` doğrulaması ve edge-case temizliği)
- [ ] **Faz 3:** Günlük engelleme sayacı, isteğe bağlı mola/uyarı ekranı ve pil optimizasyonu yönlendirmesi
