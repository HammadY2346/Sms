package com.hammad.smslink;

import android.app.Application;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Module entry point (assets/xposed_init). Runs only inside com.android.phone, which holds the
 * telephony permissions needed to inject an inbound SMS. Once a Context is available it starts the
 * network listener that receives forwarded texts from the SIM phone.
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
                Lg.e("no Context in com.android.phone after 90s; listener not started", null);
                return;
            }
            try {
                Listener.start(app);
            } catch (Throwable t) {
                Lg.e("listener start failed", t);
            }
        }, "smslink-init").start();
    }
}
