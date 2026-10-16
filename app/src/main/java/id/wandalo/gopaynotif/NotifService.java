package id.wandalo.gopaynotif;

import android.app.Notification;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baca notifikasi dari aplikasi terpilih, parse nominal otomatis,
 * teruskan ke webhook. Tetap jalan walau layar mati (system-managed).
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

        Log.i(TAG, "[" + appName + "] " + title + " | " + body + " | Rp" + amount);

        // Simpan ke history lokal
        HistoryDb db = new HistoryDb(this);
        db.add(appName, pkg, title, body, amount, System.currentTimeMillis());
        db.close();

        String webhook = prefs.getString("webhook", "");
        if (webhook.isEmpty()) return;

        final long finalAmount = amount;
        final String payload = "{\"source\":" + jsonStr(appName) +
                ",\"package\":" + jsonStr(pkg) +
                ",\"title\":" + jsonStr(title) +
                ",\"text\":" + jsonStr(body) +
                ",\"amount\":" + finalAmount +
                ",\"time\":" + System.currentTimeMillis() + "}";

        // Kirim langsung tanpa delay (realtime)
        new Thread(() -> {
            try {
                URL url = new URL(webhook);
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setDoOutput(true);
                byte[] data = payload.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(data);
                }
                Log.i(TAG, "Webhook terkirim: " + c.getResponseCode());
                c.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Gagal kirim webhook: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "Listener connected");
        getSharedPreferences("cfg", MODE_PRIVATE)
                .edit().putBoolean("listener_ok", true).apply();
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.w(TAG, "Listener disconnected, minta rebind");
        getSharedPreferences("cfg", MODE_PRIVATE)
                .edit().putBoolean("listener_ok", false).apply();
        // Minta sistem rebind
        requestRebind(new android.content.ComponentName(this, NotifService.class));
    }

    /** Parse "Rp 1.250.000" atau "Rp10.500,00" jadi 1250000 / 10500 */
    public static long parseAmount(String s) {
        if (s == null) return 0;
        Matcher m = RP_PATTERN.matcher(s);
        if (!m.find()) return 0;
        try {
            String num = m.group(1);
            // Hilangkan pemisah ribuan (titik), ubah koma jadi titik desimal
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

    private static String jsonStr(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
