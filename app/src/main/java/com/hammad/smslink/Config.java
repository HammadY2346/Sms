package com.hammad.smslink;

import java.util.UUID;

/** Fixed settings for the module. To change them, edit here and rebuild. */
public final class Config {
    private Config() {}

    /** TCP port the receiver listens on (WiFi path), inside com.android.phone. */
    public static final int PORT = 8787;

    /** Shared secret every forwarded message must carry. Keep it private; change it (and rebuild)
     *  on both phones' builds if you ever share the build. */
    public static final String TOKEN = "sl-9c4f1a7e2b";

    /** RFCOMM service UUID for the Bluetooth (SPP) link. Must match on both phones. */
    public static final UUID BT_UUID = UUID.fromString("7c3e1f00-5a2b-4c6d-8e9f-0a1b2c3d4e5f");

    /** Broadcast the app sends to com.android.phone to ask the hook to inject a message
     *  (used by the Bluetooth path; the WiFi path injects directly inside com.android.phone). */
    public static final String ACTION_INJECT = "com.hammad.smslink.INJECT";
}
