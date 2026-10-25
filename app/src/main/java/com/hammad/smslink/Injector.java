package com.hammad.smslink;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsManager;

/**
 * Turns forwarded text into a real inbound SMS on this phone.
 *
 * Primary path: SmsManager.injectSmsPdu — feeds the PDU through the normal receive pipeline, so the
 * inbox, the SMS_RECEIVED broadcast and Google's SMS Retriever (used for OTP auto-fill) all fire,
 * exactly as if the text had arrived over the air. This needs the telephony permissions that
 * com.android.phone has, which is why the module runs there.
 *
 * Fallback (if inject throws, e.g. no active subscription): write the row into the SMS inbox and
 * send the SMS_RECEIVED broadcast ourselves. The message then shows up and keyboards can still
 * offer it, though strict SMS Retriever auto-fill may not trigger.
 */
public final class Injector {
    private Injector() {}

    static boolean inject(Context ctx, String from, String text) {
        byte[] pdu;
        try {
            pdu = Pdu.buildDeliver(from, text);
        } catch (Throwable t) {
            Lg.e("PDU build failed", t);
            return false;
        }
        Lg.i("pdu(3gpp)=" + Pdu.hex(pdu));

        // Primary: inject through the telephony framework.
        try {
            SmsManager sm = null;
            try { sm = ctx.getSystemService(SmsManager.class); } catch (Throwable ignored) {}
            if (sm == null) sm = SmsManager.getDefault();
            sm.injectSmsPdu(pdu, "3gpp", null);
            Lg.i("injectSmsPdu OK");
            return true;
        } catch (Throwable t) {
            Lg.e("injectSmsPdu failed; trying inbox+broadcast fallback", t);
        }

        // Fallback: insert into the inbox and broadcast.
        boolean any = false;
        try {
            ContentValues v = new ContentValues();
            v.put("address", (from == null || from.isEmpty()) ? "Unknown" : from);
            v.put("body", text);
            v.put("date", System.currentTimeMillis());
            v.put("read", 0);
            v.put("seen", 0);
            v.put("type", 1); // MESSAGE_TYPE_INBOX
            Uri u = ctx.getContentResolver().insert(Uri.parse("content://sms/inbox"), v);
            Lg.i("inbox insert: " + u);
            any = true;
        } catch (Throwable t) {
            Lg.e("inbox insert failed", t);
        }
        try {
            Intent i = new Intent("android.provider.Telephony.SMS_RECEIVED");
            i.putExtra("pdus", new Object[]{pdu});
            i.putExtra("format", "3gpp");
            i.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            ctx.sendBroadcast(i);
            Lg.i("SMS_RECEIVED broadcast sent");
            any = true;
        } catch (Throwable t) {
            Lg.e("broadcast failed", t);
        }
        return any;
    }
}
