package com.hammad.smslink;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Sender side. Turns a received SMS into JSON and sends it to the receiver over WiFi and/or Bluetooth. */
public final class Forwarder {
    private Forwarder() {}

    static void forward(Context ctx, String from, String text) {
        String json;
        try {
            JSONObject j = new JSONObject();
            j.put("token", Config.TOKEN);
            j.put("from", from == null ? "" : from);
            j.put("text", text == null ? "" : text);
            json = j.toString();
        } catch (Throwable t) { Lg.e("forward: json build", t); return; }

        if (Prefs.wifi(ctx)) sendWifi(ctx, json);
        if (Prefs.bt(ctx)) sendBt(ctx, json);
    }

    private static void sendWifi(Context ctx, String json) {
        final String ip = Prefs.receiverIp(ctx);
        if (ip == null || ip.isEmpty()) { Lg.e("WiFi: no receiver IP set", null); return; }
        new Thread(() -> {
            try {
                URL u = new URL("http://" + ip + ":" + Config.PORT + "/");
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(6000);
                c.setReadTimeout(6000);
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                byte[] b = json.getBytes(StandardCharsets.UTF_8);
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream o = c.getOutputStream()) { o.write(b); }
                Lg.i("WiFi forward -> " + ip + " : " + c.getResponseCode());
                c.disconnect();
            } catch (Throwable t) { Lg.e("WiFi forward failed", t); }
        }, "smslink-wifi").start();
    }

    private static void sendBt(Context ctx, String json) {
        final String mac = Prefs.receiverBt(ctx);
        if (mac == null || mac.isEmpty()) { Lg.e("Bluetooth: no receiver device set", null); return; }
        new Thread(() -> {
            BluetoothSocket s = null;
            try {
                BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ad = (bm != null) ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
                if (ad == null) { Lg.e("Bluetooth: no adapter", null); return; }
                BluetoothDevice dev = ad.getRemoteDevice(mac);
                try { ad.cancelDiscovery(); } catch (Throwable ignored) {}
                s = dev.createRfcommSocketToServiceRecord(Config.BT_UUID);
                s.connect();
                OutputStream o = s.getOutputStream();
                o.write((json + "\n").getBytes(StandardCharsets.UTF_8));
                o.flush();
                Lg.i("Bluetooth forward -> " + mac + " ok");
            } catch (Throwable t) {
                Lg.e("Bluetooth forward failed (paired? receiver's BT server running?)", t);
            } finally {
                if (s != null) try { s.close(); } catch (Throwable ignored) {}
            }
        }, "smslink-bt").start();
    }
}
