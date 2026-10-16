# Notif Forwarder

Aplikasi Android yang meneruskan notifikasi dari aplikasi terpilih ke webhook secara realtime.

## Cara kerja

- `NotificationListenerService` membaca notifikasi dari aplikasi yang dipilih user
- Nominal otomatis di-parse dari teks notifikasi (format Rupiah Indonesia)
- Data dikirim via HTTP POST (JSON) ke webhook: `source`, `package`, `title`, `text`, `amount`, `time`
- Riwayat 200 notifikasi terakhir tersimpan lokal (SQLite)

## Build

Tanpa Android Studio / Gradle — build manual dengan `aapt2`, `javac`, `d8`:

```bash
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<android-sdk>
BT=$ANDROID_HOME/build-tools/34.0.0
PLAT=$ANDROID_HOME/platforms/android-34/android.jar

rm -rf build && mkdir -p build/gen build/obj build/dex
$BT/aapt2 compile --dir app/src/main/res -o build/res.zip
$BT/aapt2 link -o build/app-unsigned.apk --manifest app/src/main/AndroidManifest.xml \
  -I $PLAT --java build/gen build/res.zip
$JAVA_HOME/bin/javac -encoding UTF-8 -source 17 -target 17 -cp $PLAT \
  -d build/obj build/gen/id/wandalo/gopaynotif/R.java \
  app/src/main/java/id/wandalo/gopaynotif/*.java
$BT/d8 --lib $PLAT --output build/dex $(find build/obj -name "*.class")
cp build/app-unsigned.apk build/app.apk
cd build && zip -j app.apk dex/classes.dex
$BT/zipalign -f 4 app.apk app-aligned.apk
$BT/apksigner sign --ks <keystore> --out app-signed.apk app-aligned.apk
```

## Struktur

- `app/src/main/java/id/wandalo/gopaynotif/MainActivity.java` — UI (tab Status/Aplikasi/Riwayat, gaya Neo-Brutalism)
- `app/src/main/java/id/wandalo/gopaynotif/NotifService.java` — listener notifikasi + parse nominal + kirim webhook
- `app/src/main/java/id/wandalo/gopaynotif/HistoryDb.java` — SQLite riwayat notifikasi
- `app/src/main/AndroidManifest.xml` — permission (termasuk `QUERY_ALL_PACKAGES`)

## Server

Webhook default: `https://gopay.wandalo.dev/api/notif`
Format payload: `{"source","package","title","text","amount","time"}`
