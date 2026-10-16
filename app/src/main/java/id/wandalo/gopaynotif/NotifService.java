package id.wandalo.gopaynotif;

import android.app.Notification;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baca notifikasi dari aplikasi terpilih, parse nominal otomatis,
 * filter (nominal min + kata kunci), teruskan ke webhook.
 * Gagal kirim -> masuk antrean offline, dicoba lagi otomatis.
 */
public class NotifService extends NotificationListenerService {
    private static final String TAG = "NotifForwarder";

    // Pattern nominal Indonesia: Rp 1, Rp1.000, Rp 50.000, Rp10.500,00
    private static final Pattern RP_PATTERN =
            Pattern.compile("Rp\\.?\\s*([\\d.]+(?:,[\\d]{1,2})?)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (getPackageName().equals(pkg)) return;

        SharedPreferences prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        Set<String> enabled = prefs.getStringSet("apps", null);
        if (enabled == null || !enabled.contains(pkg)) return;

        String appName = appNameFor(pkg);

        Notification n = sbn.getNotification();
        Bundle extras = n.extras;
        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");
        String bigText = "";
        try {
            CharSequence cs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (cs != null) bigText = cs.toString();
        } catch (Exception ignored) {}

        String body = !bigText.isEmpty() ? bigText : text;
        if (title.isEmpty() && body.isEmpty()) return;

        // Parse nominal otomatis
        long amount = parseAmount(body);
        if (amount == 0) amount = parseAmount(title);

        // Filter: nominal minimal
        long minAmount = 0;
        try {
            minAmount = Long.parseLong(prefs.getString("min_amount", "0"));
        } catch (Exception ignored) {}
        if (minAmount > 0 && amount < minAmount) {
            Log.i(TAG, "Difilter (min Rp" + minAmount + "): " + title);
            return;
        }

        // Filter: kata kunci (kosong = semua lolos)
        String kwRaw = prefs.getString("keywords", "").trim();
        if (!kwRaw.isEmpty()) {
            String hay = (title + " " + body).toLowerCase();
            boolean hit = false;
            for (String kw : kwRaw.split(",")) {
                kw = kw.trim().toLowerCase();
                if (!kw.isEmpty() && hay.contains(kw)) {
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                Log.i(TAG, "Difilter (kata kunci): " + title);
                return;
            }
        }

        Log.i(TAG, "[" + appName + "] " + title + " | " + body + " | Rp" + amount);

        // Simpan ke history lokal
        HistoryDb db = new HistoryDb(this);
        db.add(appName, pkg, title, body, amount, System.currentTimeMillis());
        db.close();

        String webhook = prefs.getString("webhook", "");
        if (webhook.isEmpty()) return;
        String token = prefs.getString("webhook_token", "");
        String deviceId = Webhook.deviceId(this);
        String deviceName = prefs.getString("device_name", Build.MODEL);

        final String payload = "{\"token\":" + Webhook.jsonStr(token) +
                ",\"device_id\":" + Webhook.jsonStr(deviceId) +
                ",\"device_name\":" + Webhook.jsonStr(deviceName) +
                ",\"source\":" + Webhook.jsonStr(appName) +
                ",\"package\":" + Webhook.jsonStr(pkg) +
                ",\"title\":" + Webhook.jsonStr(title) +
                ",\"text\":" + Webhook.jsonStr(body) +
                ",\"amount\":" + amount +
                ",\"time\":" + System.currentTimeMillis() + "}";

        final String desc = appName + " Rp" +
                String.format("%,d", amount).replace(',', '.');

        // Kirim langsung (realtime); gagal -> antrean
        new Thread(() -> {
            // Coba kirim antrean lama dulu
            Webhook.flushPending(NotifService.this);
            int code = Webhook.send(NotifService.this, webhook, payload);
            if (code == 200) {
                Webhook.markSent(NotifService.this, desc);
            } else {
                new PendingDb(NotifService.this)
                        .add(payload, System.currentTimeMillis());
                Webhook.notifyFailure(NotifService.this,
                        "1 notifikasi " + appName + " belum terkirim");
                Log.w(TAG, "Masuk antrean, code=" + code);
            }
        }).start();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Listener connected");
        getSharedPreferences("cfg", MODE_PRIVATE)
                .edit().putBoolean("listener_ok", true).apply();
        // Flush antrean saat listener tersambung
        new Thread(() -> Webhook.flushPending(this)).start();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Listener disconnected, minta rebind");
        getSharedPreferences("cfg", MODE_PRIVATE)
                .edit().putBoolean("listener_ok", false).apply();
        requestRebind(new android.content.ComponentName(this, NotifService.class));
    }

    /** Parse "Rp 1.250.000" atau "Rp10.500,00" jadi 1250000 / 10500 */
    public static long parseAmount(String s) {
        if (s == null) return 0;
        Matcher m = RP_PATTERN.matcher(s);
        if (!m.find()) return 0;
        try {
            String num = m.group(1);
            num = num.replace(".", "");
            if (num.contains(",")) {
                num = num.replace(",", ".");
            }
            double v = Double.parseDouble(num);
            return Math.round(v);
        } catch (Exception e) {
            return 0;
        }
    }

    private String appNameFor(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (Exception e) {
            return pkg;
        }
    }
}
