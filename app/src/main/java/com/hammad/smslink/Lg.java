package com.hammad.smslink;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/** Logs to both the LSPosed log (visible in the LSPosed manager) and logcat, tag "SMSLink". */
public final class Lg {
    private Lg() {}
    static final String T = "SMSLink";

    static void i(String m) {
        try { XposedBridge.log(T + ": " + m); } catch (Throwable ignored) {}
        try { Log.i(T, m); } catch (Throwable ignored) {}
    }

    static void e(String m, Throwable t) {
        try { XposedBridge.log(T + ": " + m + (t != null ? "\n" + Log.getStackTraceString(t) : "")); } catch (Throwable ignored) {}
        try { Log.e(T, m, t); } catch (Throwable ignored) {}
    }
}
