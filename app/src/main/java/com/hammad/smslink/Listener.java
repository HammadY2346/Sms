package com.hammad.smslink;

import android.content.Context;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP listener. Runs inside com.android.phone. Accepts a POST whose JSON body is
 * {"token":"...","from":"...","text":"..."} and, when the token matches, injects the text as an
 * inbound SMS. Kept deliberately tiny — no HTTP library, just enough to read one POST.
 */
public final class Listener {
    private Listener() {}
    private static volatile boolean running = false;

    static synchronized void start(Context ctx) {
        if (running) return;
        running = true;
        new Thread(() -> loop(ctx), "smslink-listener").start();
    }

    private static void loop(Context ctx) {
        while (running) {
            ServerSocket ss = null;
            try {
                ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress((java.net.InetAddress) null, Config.PORT)); // all interfaces
                Lg.i("listening on 0.0.0.0:" + Config.PORT);
                while (running) {
                    Socket c = ss.accept();
                    try { handle(ctx, c); } catch (Throwable t) { Lg.e("request failed", t); }
                }
            } catch (Throwable t) {
                Lg.e("listener socket error (SELinux or port in use?); retrying in 5s", t);
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            } finally {
                if (ss != null) try { ss.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void handle(Context ctx, Socket raw) throws Exception {
        try (Socket s = raw) {
            s.setSoTimeout(6000);
            InputStream in = s.getInputStream();

            // Read headers up to the blank line.
            StringBuilder hb = new StringBuilder();
            int b;
            while ((b = in.read()) != -1) {
                hb.append((char) b);
                int n = hb.length();
                if (n >= 4 && hb.charAt(n - 4) == '\r' && hb.charAt(n - 3) == '\n'
                        && hb.charAt(n - 2) == '\r' && hb.charAt(n - 1) == '\n') break;
                if (n > 16384) break;
            }
            int clen = 0;
            for (String line : hb.toString().split("\r\n")) {
                int i = line.indexOf(':');
                if (i > 0 && line.substring(0, i).trim().equalsIgnoreCase("Content-Length")) {
                    try { clen = Integer.parseInt(line.substring(i + 1).trim()); } catch (Exception ignored) {}
                }
            }
            byte[] body = new byte[Math.max(0, clen)];
            int off = 0;
            while (off < clen) {
                int r = in.read(body, off, clen - off);
                if (r < 0) break;
                off += r;
            }
            String bodyStr = new String(body, 0, off, StandardCharsets.UTF_8);

            int code;
            String resp;
            try {
                JSONObject j = new JSONObject(bodyStr);
                String token = j.optString("token", "");
                String from = firstNonEmpty(j.optString("from", ""), j.optString("sender", ""), j.optString("address", ""));
                String text = firstNonEmpty(j.optString("text", ""), j.optString("msg", ""),
                        j.optString("content", ""), j.optString("org_content", ""), j.optString("body", ""));
                if (!Config.TOKEN.equals(token)) {
                    code = 401; resp = "bad token";
                    Lg.e("rejected: bad token from " + s.getInetAddress(), null);
                } else if (text.isEmpty()) {
                    code = 400; resp = "no text";
                } else {
                    Lg.i("forward from '" + from + "' (" + text.length() + " chars)");
                    boolean ok = Injector.inject(ctx, from, text);
                    code = ok ? 200 : 500;
                    resp = ok ? "OK" : "inject failed";
                }
            } catch (Throwable t) {
                code = 400; resp = "bad json";
                Lg.e("parse error", t);
            }

            OutputStream out = s.getOutputStream();
            byte[] rb = resp.getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 " + code + " X\r\n"
                    + "Content-Length: " + rb.length + "\r\n"
                    + "Content-Type: text/plain\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(rb);
            out.flush();
        }
    }

    private static String firstNonEmpty(String... v) {
        for (String s : v) if (s != null && !s.isEmpty()) return s;
        return "";
    }
}
