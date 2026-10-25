package com.hammad.smslink;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;

/** Sender side. Catches incoming SMS on the SIM phone and forwards each one to the receiver. */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Prefs.role(ctx) != Prefs.SENDER) return;
        Lg.setup(ctx);
        final PendingResult pending = goAsync();
        try {
            Bundle b = intent.getExtras();
            if (b == null) { pending.finish(); return; }
            Object[] pdus = (Object[]) b.get("pdus");
            if (pdus == null) { pending.finish(); return; }
            String format = b.getString("format");

            String from = null;
            StringBuilder body = new StringBuilder();
            for (Object p : pdus) {
                SmsMessage m = (Build.VERSION.SDK_INT >= 23 && format != null)
                        ? SmsMessage.createFromPdu((byte[]) p, format)
                        : SmsMessage.createFromPdu((byte[]) p);
                if (m == null) continue;
                if (from == null) from = m.getOriginatingAddress();
                body.append(m.getMessageBody());
            }
            Lg.i("SMS from '" + from + "' (" + body.length() + " chars); forwarding");
            Forwarder.forward(ctx, from, body.toString());
        } catch (Throwable t) {
            Lg.e("SmsReceiver", t);
        } finally {
            // Give the forward threads a moment, then release.
            new Thread(() -> {
                try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
                try { pending.finish(); } catch (Throwable ignored) {}
            }, "smslink-sms-finish").start();
        }
    }
}
