package com.hammad.smslink;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Receiver side, Bluetooth path. Runs in the app's foreground service. Accepts RFCOMM connections
 *  from the sender phone and hands each message to com.android.phone (via INJECT broadcast) to inject. */
public final class BtServer {
    private final Context ctx;
    private volatile boolean running;
    private BluetoothServerSocket server;

    BtServer(Context c) { ctx = c; }

    void start() {
        if (running) return;
        running = true;
        new Thread(this::loop, "smslink-bt-server").start();
    }

    void stopServer() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
    }

    private void loop() {
        while (running) {
            try {
                BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter ad = (bm != null) ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
                if (ad == null || !ad.isEnabled()) {
                    Lg.e("BT server: adapter off; retry in 10s", null);
                    Thread.sleep(10000);
                    continue;
                }
                server = ad.listenUsingRfcommWithServiceRecord("SMSLink", Config.BT_UUID);
                Lg.i("BT server listening");
                while (running) {
                    BluetoothSocket s = server.accept();
                    handle(s);
                }
            } catch (Throwable t) {
                if (running) {
                    Lg.e("BT server error; retry in 5s", t);
                    try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
                }
            } finally {
                try { if (server != null) server.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private void handle(BluetoothSocket s) {
        try (BluetoothSocket sock = s) {
            BufferedReader r = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
            String line = r.readLine();
            if (line == null) return;
            JSONObject j = new JSONObject(line);
            if (!Config.TOKEN.equals(j.optString("token", ""))) { Lg.e("BT: bad token", null); return; }
            String from = j.optString("from", "");
            String text = j.optString("text", "");
            Lg.i("BT payload from '" + from + "'");
            Intent i = new Intent(Config.ACTION_INJECT).setPackage("com.android.phone");
            i.putExtra("token", Config.TOKEN);
            i.putExtra("from", from);
            i.putExtra("text", text);
            ctx.sendBroadcast(i);
            Lg.i("BT -> INJECT broadcast sent");
        } catch (Throwable t) {
            Lg.e("BT handle", t);
        }
    }
}
