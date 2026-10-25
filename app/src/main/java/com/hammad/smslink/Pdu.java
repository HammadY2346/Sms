package com.hammad.smslink;

import java.io.ByteArrayOutputStream;
import java.util.Calendar;

/**
 * Builds a single-part 3GPP SMS-DELIVER PDU (the shape a phone receives over the air), so it can be
 * handed to SmsManager.injectSmsPdu. 7-bit GSM when the text fits the default alphabet, otherwise
 * UCS2. Single part only (v0.1): long texts are truncated, which is fine for OTP codes.
 */
public final class Pdu {
    private Pdu() {}

    // GSM 7-bit default alphabet; index == septet value.
    private static final String GSM =
            "@£$¥èéùìòÇ\nØø\rÅå"
            + "Δ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ"
            + " !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
            + "¿abcdefghijklmnopqrstuvwxyzäöñüà";

    private static boolean isGsm(String s) {
        for (int i = 0; i < s.length(); i++) if (GSM.indexOf(s.charAt(i)) < 0) return false;
        return true;
    }

    static byte[] buildDeliver(String from, String text) {
        boolean gsm = isGsm(text);
        int max = gsm ? 160 : 70;
        if (text.length() > max) {
            Lg.e("text longer than one SMS part (" + text.length() + "), truncating to " + max, null);
            text = text.substring(0, max);
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x00);              // SMSC: length 0 => the stack uses its default
        o.write(0x04);              // first octet: SMS-DELIVER, no more messages
        writeOriginatingAddress(o, from == null ? "" : from);
        o.write(0x00);              // TP-PID
        o.write(gsm ? 0x00 : 0x08); // TP-DCS: GSM 7-bit or UCS2
        writeTimestamp(o);
        if (gsm) {
            byte[] ud = pack7(text);
            o.write(text.length() & 0xFF);   // UDL in septets
            o.write(ud, 0, ud.length);
        } else {
            byte[] ud = ucs2(text);
            o.write(ud.length & 0xFF);       // UDL in octets
            o.write(ud, 0, ud.length);
        }
        return o.toByteArray();
    }

    private static void writeOriginatingAddress(ByteArrayOutputStream o, String addr) {
        boolean numeric = !addr.isEmpty() && addr.matches("\\+?[0-9]+");
        if (numeric) {
            String d = addr.startsWith("+") ? addr.substring(1) : addr;
            o.write(d.length() & 0xFF);                     // length in digits (semi-octets)
            o.write(addr.startsWith("+") ? 0x91 : 0x81);    // type-of-address
            for (int i = 0; i < d.length(); i += 2) {
                int lo = d.charAt(i) - '0';
                int hi = (i + 1 < d.length()) ? (d.charAt(i + 1) - '0') : 0xF;
                o.write((hi << 4) | lo);
            }
        } else {
            // Alphanumeric sender (e.g. a bank short name). 7-bit packed, length in semi-octets.
            String a = addr.isEmpty() ? "Unknown" : (addr.length() > 11 ? addr.substring(0, 11) : addr);
            byte[] packed = pack7(a);
            o.write((packed.length * 2) & 0xFF);
            o.write(0xD0);                                   // type-of-address: alphanumeric
            o.write(packed, 0, packed.length);
        }
    }

    private static void writeTimestamp(ByteArrayOutputStream o) {
        Calendar c = Calendar.getInstance();
        o.write(swap(c.get(Calendar.YEAR) % 100));
        o.write(swap(c.get(Calendar.MONTH) + 1));
        o.write(swap(c.get(Calendar.DAY_OF_MONTH)));
        o.write(swap(c.get(Calendar.HOUR_OF_DAY)));
        o.write(swap(c.get(Calendar.MINUTE)));
        o.write(swap(c.get(Calendar.SECOND)));
        int tzQuarters = (c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / (15 * 60 * 1000);
        boolean neg = tzQuarters < 0;
        int tz = swap(Math.abs(tzQuarters));
        if (neg) tz |= 0x08; // sign bit is bit 3 of the high nibble (after swap)
        o.write(tz);
    }

    /** Swap the two decimal digits of v into semi-octet (BCD) order. */
    private static int swap(int v) {
        v = ((v % 100) + 100) % 100;
        return ((v % 10) << 4) | (v / 10);
    }

    private static byte[] pack7(String s) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int bits = 0;
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            int idx = GSM.indexOf(s.charAt(i));
            if (idx < 0) idx = 0x20; // space
            acc |= (idx << bits);
            bits += 7;
            while (bits >= 8) {
                o.write(acc & 0xFF);
                acc >>= 8;
                bits -= 8;
            }
        }
        if (bits > 0) o.write(acc & 0xFF);
        return o.toByteArray();
    }

    private static byte[] ucs2(String s) {
        byte[] b = new byte[s.length() * 2];
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b[i * 2] = (byte) (ch >> 8);
            b[i * 2 + 1] = (byte) (ch & 0xFF);
        }
        return b;
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02X", x));
        return sb.toString();
    }
}
