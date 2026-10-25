package com.hammad.smslink;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.ScrollView;
import android.widget.TextView;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Info screen: shows this phone's addresses and how to set up the forwarder on the SIM phone. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = (int) (16 * getResources().getDisplayMetrics().density);

        TextView t = new TextView(this);
        t.setPadding(p, p, p, p);
        t.setTextIsSelectable(true);
        t.setMovementMethod(new ScrollingMovementMethod());

        StringBuilder s = new StringBuilder();
        s.append("SMS Link — receives texts from your other phone\n\n");
        s.append("This phone is the RECEIVER. Setup:\n");
        s.append("1. In LSPosed, enable \"SMS Link\" and tick com.android.phone in its scope.\n");
        s.append("2. Reboot.\n");
        s.append("3. On the SIM phone, forward texts to one of the addresses below.\n\n");

        s.append("This phone's addresses:\n");
        for (String ip : ipv4()) {
            s.append("  http://").append(ip).append(":").append(Config.PORT).append("/\n");
        }
        s.append("\nPort:  ").append(Config.PORT).append("\n");
        s.append("Token: ").append(Config.TOKEN).append("\n\n");

        s.append("On the SIM phone (your A71):\n");
        s.append("Install an SMS-forwarder app (e.g. \"SmsForwarder\" by pppscn) and add a Webhook sender:\n");
        s.append("  URL:    http://<this-phone-ip>:").append(Config.PORT).append("/\n");
        s.append("  Method: POST\n");
        s.append("  Header: Content-Type: application/json\n");
        s.append("  Body:   {\"token\":\"").append(Config.TOKEN)
                .append("\",\"from\":\"[from]\",\"text\":\"[org_content]\"}\n\n");
        s.append("(placeholders depend on the app; SmsForwarder uses [from] and [org_content].)\n\n");

        s.append("Both phones must share a network — easiest is to use the A71 as a hotspot and connect this phone to it.\n\n");
        s.append("When a text arrives, watch the LSPosed log (tag \"SMSLink\") to see whether inject succeeded.\n");
        if (BuildConfig.DEBUG) {
            s.append("\nThis is a DEBUG build: it also writes a log on this phone.\n");
            s.append("Find it with root:  su -c 'find /data -name smslink.log'\n");
        }

        t.setText(s);

        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }

    private List<String> ipv4() {
        List<String> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address) r.add(a.getHostAddress());
                }
            }
        } catch (Throwable ignored) {}
        if (r.isEmpty()) r.add("<connect to Wi-Fi to see an address>");
        return r;
    }
}
