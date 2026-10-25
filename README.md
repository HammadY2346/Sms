# SMS Link

An LSPosed module for a phone **without a working SIM** (e.g. a non-PTA RedMagic 10S Pro) that lets
it receive the SMS — especially login/OTP codes — that arrive on your **other** phone that holds the
SIM (e.g. a PTA-approved Galaxy A71). The code lands on this phone as a *real* incoming SMS, so apps
auto-read the OTP exactly as if the text had come in over the air.

## How it works

Two phones:

- **SIM phone (sender).** Runs any SMS-forwarder app that can POST a webhook (e.g.
  [SmsForwarder](https://github.com/pppscn/SmsForwarder)). On each incoming SMS it POSTs the sender
  and text to the receiver.
- **This phone (receiver).** This module runs inside `com.android.phone` (the telephony process,
  which holds the permission to inject an SMS). It listens on a local port, and when a forwarded
  text arrives with the right token it builds a 3GPP SMS-DELIVER PDU and calls
  `SmsManager.injectSmsPdu`, delivering it through the normal receive pipeline — inbox,
  `SMS_RECEIVED` broadcast, and Google's SMS Retriever (OTP auto-fill).

Both phones must share a network. Easiest: make the SIM phone a hotspot and connect this phone to it.

## Setup

1. Flash/install the APK, enable **SMS Link** in LSPosed, tick **com.android.phone** in its scope, reboot.
2. Open the app to see this phone's IP, the port and the token.
3. On the SIM phone, point an SMS forwarder at `http://<this-phone-ip>:8787/` with a JSON body:
   `{"token":"<token>","from":"[from]","text":"[org_content]"}`.
4. Send yourself a test SMS. Watch the LSPosed log (tag `SMSLink`) on this phone.

## Status

v0.1 — single-part messages (fine for OTP). Experimental: whether `injectSmsPdu` works on a device
with no active SIM is the main thing to confirm from the log; there's an inbox+broadcast fallback if
it doesn't.

## Security

The token is a shared secret in `Config.java`; only requests that present it are accepted. Anyone on
the same network who knows the token could inject an SMS onto this phone, so keep the build private
and change the token (then rebuild) before sharing it anywhere.
