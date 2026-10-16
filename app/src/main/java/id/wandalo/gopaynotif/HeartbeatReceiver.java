package id.wandalo.gopaynotif;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Alarm berkala (15 mnt): flush antrean offline + kirim heartbeat.
 * Menjadwalkan ulang dirinya sendiri (tahan Doze, tanpa exact-alarm permission).
 */
public class HeartbeatReceiver extends BroadcastReceiver {
    private static final String TAG = "NotifForwarder";
    private static final long INTERVAL_MS = 15 * 60 * 1000;
    private static final int REQ = 4242;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i(TAG, "Heartbeat tick");
        new Thread(() -> {
            try {
                Webhook.sendHeartbeat(ctx.getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Heartbeat error: " + e.getMessage());
            }
            schedule(ctx.getApplicationContext());
        }).start();
    }

    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager)
                    ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, HeartbeatReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(ctx, REQ, i, flags);
            long next = SystemClock.elapsedRealtime() + INTERVAL_MS;
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    next, pi);
        } catch (Exception e) {
            Log.e(TAG, "schedule gagal: " + e.getMessage());
        }
    }

    public static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager)
                    ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, HeartbeatReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            am.cancel(PendingIntent.getBroadcast(ctx, REQ, i, flags));
        } catch (Exception ignored) {}
    }
}
