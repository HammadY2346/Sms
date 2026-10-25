package com.hammad.smslink;

import android.content.Context;
import android.content.SharedPreferences;

/** App-side settings (this phone's role and where it forwards to). Private to the app's own process;
 *  the hook in com.android.phone doesn't need these — it only verifies the token. */
public final class Prefs {
    private Prefs() {}

    public static final int OFF = 0, RECEIVER = 1, SENDER = 2;

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("smslink", Context.MODE_PRIVATE);
    }

    public static int role(Context c) { return p(c).getInt("role", OFF); }
    public static void setRole(Context c, int v) { p(c).edit().putInt("role", v).apply(); }

    public static boolean wifi(Context c) { return p(c).getBoolean("wifi", true); }
    public static void setWifi(Context c, boolean v) { p(c).edit().putBoolean("wifi", v).apply(); }

    public static boolean bt(Context c) { return p(c).getBoolean("bt", false); }
    public static void setBt(Context c, boolean v) { p(c).edit().putBoolean("bt", v).apply(); }

    /** Sender → receiver WiFi target (the receiver phone's IP). */
    public static String receiverIp(Context c) { return p(c).getString("rx_ip", ""); }
    public static void setReceiverIp(Context c, String v) { p(c).edit().putString("rx_ip", v).apply(); }

    /** Sender → receiver Bluetooth target (the receiver phone's MAC, picked from paired devices). */
    public static String receiverBt(Context c) { return p(c).getString("rx_bt", ""); }
    public static String receiverBtName(Context c) { return p(c).getString("rx_bt_name", ""); }
    public static void setReceiverBt(Context c, String mac, String name) {
        p(c).edit().putString("rx_bt", mac).putString("rx_bt_name", name).apply();
    }
}
