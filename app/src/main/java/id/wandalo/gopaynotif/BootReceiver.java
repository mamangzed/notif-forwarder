package id.wandalo.gopaynotif;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Setelah HP restart: jadwalkan ulang heartbeat + flush antrean. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("NotifForwarder", "Boot completed, jadwalkan heartbeat");
            HeartbeatReceiver.schedule(ctx.getApplicationContext());
            new Thread(() -> Webhook.flushPending(ctx.getApplicationContext()))
                    .start();
        }
    }
}
