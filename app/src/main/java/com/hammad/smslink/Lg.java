package com.hammad.smslink;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

/**
 * Logs to the LSPosed log and logcat (tag "SMSLink"). In a debug build it also appends every line
 * to a file in com.android.phone's data dir, so there's a durable record without the LSPosed manager
 * open. Read it with root:  su -c 'find /data -name smslink.log'
 */
public final class Lg {
    private Lg() {}
    static final String T = "SMSLink";

    private static File file;
    private static final SimpleDateFormat TS = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    /** Debug builds only: start writing to <phone data dir>/files/smslink.log. */
    static void setup(Context ctx) {
        if (!BuildConfig.DEBUG || file != null) return;
        try {
            file = new File(ctx.getFilesDir(), "smslink.log");
            append("---- log opened (v" + BuildConfig.VERSION_NAME + ") ----");
        } catch (Throwable t) {
            Log.e(T, "could not open debug log", t);
        }
    }

    static void i(String m) { out("I " + m, null); }
    static void e(String m, Throwable t) { out("E " + m, t); }

    private static void out(String m, Throwable t) {
        String line = (t != null) ? m + "\n" + Log.getStackTraceString(t) : m;
        try { XposedBridge.log(T + ": " + line); } catch (Throwable ignored) {}
        try { Log.println(t != null ? Log.ERROR : Log.INFO, T, line); } catch (Throwable ignored) {}
        if (file != null) append(line);
    }

    private static synchronized void append(String line) {
        try (FileWriter w = new FileWriter(file, true)) {
            w.write(TS.format(new Date()) + " " + line + "\n");
        } catch (Throwable ignored) {}
    }
}
