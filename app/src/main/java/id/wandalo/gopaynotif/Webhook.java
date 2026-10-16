package id.wandalo.gopaynotif;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Kirim payload ke webhook, antrean offline, heartbeat, notif lokal gagal. */
public class Webhook {
    private static final String TAG = "NotifForwarder";

    /** Kirim satu payload. Return HTTP code, atau -1 kalau gagal total. */
    public static int send(Context ctx, String url, String payload) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
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
            return c.getResponseCode();
        } catch (Exception e) {
            Log.e(TAG, "Webhook gagal: " + e.getMessage());
            return -1;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Coba kirim semua antrean. Return jumlah yang berhasil. */
    public static int flushPending(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("cfg",
                Context.MODE_PRIVATE);
        String url = prefs.getString("webhook", "");
        if (url.isEmpty()) return 0;
        PendingDb db = new PendingDb(ctx);
        List<PendingDb.Item> items = db.list();
        int ok = 0;
        for (PendingDb.Item it : items) {
            int code = send(ctx, url, it.payload);
            if (code == 200) {
                db.remove(it.id);
                ok++;
            } else {
                break; // berhenti di kegagalan pertama, coba lagi nanti
            }
        }
        db.close();
        if (ok > 0) markSent(ctx, ok + " antrean terkirim");
        return ok;
    }

    /** Kirim heartbeat: "HP kasir masih hidup". */
    public static void sendHeartbeat(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("cfg",
                Context.MODE_PRIVATE);
        String url = prefs.getString("webhook", "");
        if (url.isEmpty()) return;
        String token = prefs.getString("webhook_token", "");
        String deviceId = deviceId(ctx);
        String deviceName = prefs.getString("device_name", Build.MODEL);
        int pending = new PendingDb(ctx).count();
        String payload = "{\"token\":" + jsonStr(token) +
                ",\"type\":\"heartbeat\"" +
                ",\"device_id\":" + jsonStr(deviceId) +
                ",\"device_name\":" + jsonStr(deviceName) +
                ",\"pending\":" + pending +
                ",\"time\":" + System.currentTimeMillis() + "}";
        // Flush dulu, baru heartbeat
        flushPending(ctx);
        int code = send(ctx, url, payload);
        Log.i(TAG, "Heartbeat: " + code);
    }

    /** Catat pengiriman terakhir yang sukses (untuk indikator UI). */
    public static void markSent(Context ctx, String desc) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM HH:mm:ss",
                Locale.getDefault());
        ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                .putString("last_sent",
                        sdf.format(new Date()) + " — " + desc)
                .apply();
    }

    public static String deviceId(Context ctx) {
        String id = Settings.Secure.getString(ctx.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        return id != null ? id : "unknown";
    }

    /** Notifikasi lokal di HP saat pengiriman gagal (throttle 15 menit). */
    public static void notifyFailure(Context ctx, String desc) {
        SharedPreferences prefs = ctx.getSharedPreferences("cfg",
                Context.MODE_PRIVATE);
        long last = prefs.getLong("last_fail_notify", 0);
        if (System.currentTimeMillis() - last < 15 * 60 * 1000) return;
        prefs.edit().putLong("last_fail_notify",
                System.currentTimeMillis()).apply();

        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String chId = "kirim_gagal";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(chId,
                        "Status Pengiriman", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            android.app.Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                b = new android.app.Notification.Builder(ctx, chId);
            } else {
                b = new android.app.Notification.Builder(ctx);
            }
            b.setContentTitle("Notif Forwarder: gagal mengirim")
                    .setContentText(desc + " — disimpan, akan dicoba lagi.")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setAutoCancel(true);
            nm.notify(9001, b.build());
        } catch (Exception e) {
            Log.e(TAG, "notify gagal: " + e.getMessage());
        }
    }

    public static String jsonStr(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
