package com.hammad.smslink;

/** Fixed settings for v0.1. To change them, edit here and rebuild. */
public final class Config {
    private Config() {}

    /** TCP port the receiver listens on, inside com.android.phone. */
    public static final int PORT = 8787;

    /** Shared secret the forwarder on the SIM phone must send in every request.
     *  Anyone on the same network who knows this could inject an SMS, so keep it private
     *  and change it (then rebuild) if you ever share the build. */
    public static final String TOKEN = "sl-9c4f1a7e2b";
}
