package com.hammad.smslink;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Receiver side. Runs only inside com.android.phone (enabled in LSPosed on the phone WITHOUT the SIM).
 * It can inject an inbound SMS because that process holds the telephony permissions. Two ways in:
 *   - WiFi: an in-process HTTP listener (Listener) injects directly.
 *   - Bluetooth: the app's foreground service receives over RFCOMM and sends an INJECT broadcast here.
 */
public final class Entry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!"com.android.phone".equals(lp.packageName)) return;
        Lg.i("loaded in com.android.phone (process " + lp.processName + ")");

        final ClassLoader cl = lp.classLoader;
        new Thread(() -> {
            Application app = null;
            for (int i = 0; i < 90 && app == null; i++) {
                try {
                    app = (Application) XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass("android.app.ActivityThread", cl), "currentApplication");
                } catch (Throwable ignored) {}
                if (app == null) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                }
            }
            if (app == null) {
                Lg.e("no Context in com.android.phone after 90s; receiver not started", null);
                return;
            }
            Lg.setup(app);
            final Context ctx = app;

            // Bluetooth path: the app forwards a received text here as a broadcast.
            try {
                BroadcastReceiver r = new BroadcastReceiver() {
                    @Override public void onReceive(Context c, Intent i) {
                        try {
                            if (!Config.TOKEN.equals(i.getStringExtra("token"))) { Lg.e("INJECT: bad token", null); return; }
                            String from = i.getStringExtra("from");
                            String text = i.getStringExtra("text");
                            Lg.i("INJECT broadcast from '" + from + "'");
                            Injector.inject(ctx, from, text == null ? "" : text);
                        } catch (Throwable t) { Lg.e("INJECT handler", t); }
                    }
                };
                IntentFilter f = new IntentFilter(Config.ACTION_INJECT);
                if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
                else ctx.registerReceiver(r, f);
                Lg.i("INJECT receiver registered");
            } catch (Throwable t) { Lg.e("register INJECT failed", t); }

            // WiFi path: in-process HTTP listener, always on while the module is loaded.
            try { Listener.start(ctx); } catch (Throwable t) { Lg.e("WiFi listener failed", t); }
        }, "smslink-init").start();
    }
}
