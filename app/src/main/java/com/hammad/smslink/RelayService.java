package com.hammad.smslink;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Foreground service for the Bluetooth receiver: it keeps the RFCOMM server alive. Only started on
 * the receiver phone when Bluetooth is enabled; the WiFi path runs in com.android.phone and needs no
 * service, and the sender path works from the SMS broadcast receiver, so this service is BT-only.
 */
public class RelayService extends Service {
    private BtServer btServer;

    @Override
    public void onCreate() {
        super.onCreate();
        Lg.setup(this);
        startForeground(1, buildNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Lg.i("RelayService start (role=" + Prefs.role(this) + ", bt=" + Prefs.bt(this) + ")");
        if (Prefs.role(this) == Prefs.RECEIVER && Prefs.bt(this)) {
            if (btServer == null) { btServer = new BtServer(this); btServer.start(); }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (btServer != null) btServer.stopServer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        String ch = "smslink";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(
                    new NotificationChannel(ch, "SMS Link", NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder b = (Build.VERSION.SDK_INT >= 26)
                ? new Notification.Builder(this, ch) : new Notification.Builder(this);
        b.setContentTitle("SMS Link");
        b.setContentText("Receiving texts over Bluetooth");
        b.setSmallIcon(android.R.drawable.stat_notify_sync);
        b.setOngoing(true);
        return b.build();
    }

    static void start(Context c) {
        Intent i = new Intent(c, RelayService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, RelayService.class));
    }
}
