package com.hammad.smslink;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** One screen for both roles: pick Receiver (SIM-less phone) or Sender (SIM phone), set the link. */
public class MainActivity extends Activity {

    private RadioGroup roleGroup;
    private CheckBox wifiCb, btCb;
    private EditText ipField;
    private TextView btSel, status;
    private Runnable pending;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int p = dp(16), g = dp(8);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(p, p, p, p);

        col.addView(label("SMS Link", 20, true));
        col.addView(label("Install this same app on both phones. On the SIM-less phone pick Receiver "
                + "(and enable it in LSPosed, scope com.android.phone). On the SIM phone pick Sender.", 13, false));

        col.addView(label("\nThis phone's role", 15, true));
        roleGroup = new RadioGroup(this);
        roleGroup.addView(radio("Off", Prefs.OFF));
        roleGroup.addView(radio("Receiver (no SIM — inject the codes here)", Prefs.RECEIVER));
        roleGroup.addView(radio("Sender (has the SIM — forward its texts)", Prefs.SENDER));
        roleGroup.check(100 + Prefs.role(this));
        col.addView(roleGroup);

        col.addView(label("\nLink over", 15, true));
        wifiCb = new CheckBox(this); wifiCb.setText("WiFi / hotspot"); wifiCb.setChecked(Prefs.wifi(this));
        btCb = new CheckBox(this); btCb.setText("Bluetooth"); btCb.setChecked(Prefs.bt(this));
        col.addView(wifiCb); col.addView(btCb);

        col.addView(label("\nSender settings (where to send)", 15, true));
        col.addView(label("Receiver phone's IP (for WiFi):", 13, false));
        ipField = new EditText(this);
        ipField.setInputType(InputType.TYPE_CLASS_TEXT);
        ipField.setHint("e.g. 192.168.43.123");
        ipField.setText(Prefs.receiverIp(this));
        col.addView(ipField);

        Button pick = new Button(this);
        pick.setText("Pick paired receiver (for Bluetooth)");
        pick.setOnClickListener(v -> pickBt());
        col.addView(pick);
        btSel = label(btLabel(), 13, false);
        col.addView(btSel);

        Button apply = new Button(this);
        apply.setText("Save & apply");
        apply.setOnClickListener(v -> apply());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = g; apply.setLayoutParams(lp);
        col.addView(apply);

        status = label("", 13, false);
        status.setTextIsSelectable(true);
        col.addView(status);
        refreshStatus();

        ScrollView sv = new ScrollView(this);
        sv.addView(col);
        setContentView(sv);
    }

    private void apply() {
        int role = roleGroup.getCheckedRadioButtonId() - 100;
        if (role < 0) role = Prefs.OFF;
        Prefs.setRole(this, role);
        Prefs.setWifi(this, wifiCb.isChecked());
        Prefs.setBt(this, btCb.isChecked());
        Prefs.setReceiverIp(this, ipField.getText().toString().trim());

        List<String> need = new ArrayList<>();
        if (role == Prefs.SENDER && !has(Manifest.permission.RECEIVE_SMS)) need.add(Manifest.permission.RECEIVE_SMS);
        if (btCb.isChecked() && Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT))
            need.add(Manifest.permission.BLUETOOTH_CONNECT);
        if (role == Prefs.RECEIVER && btCb.isChecked() && Build.VERSION.SDK_INT >= 33 && !has(Manifest.permission.POST_NOTIFICATIONS))
            need.add(Manifest.permission.POST_NOTIFICATIONS);

        pending = this::applyService;
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
        else runPending();
    }

    private void applyService() {
        if (Prefs.role(this) == Prefs.RECEIVER && Prefs.bt(this)) RelayService.start(this);
        else RelayService.stop(this);
        refreshStatus();
        Toast.makeText(this, "Applied", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == 1) runPending();
        if (req == 2) showBtDialog();
    }

    private void runPending() {
        Runnable r = pending; pending = null;
        if (r != null) r.run();
    }

    private void pickBt() {
        if (Build.VERSION.SDK_INT >= 31 && !has(Manifest.permission.BLUETOOTH_CONNECT)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }
        showBtDialog();
    }

    private void showBtDialog() {
        try {
            BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter ad = (bm != null) ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
            if (ad == null) { Toast.makeText(this, "No Bluetooth", Toast.LENGTH_SHORT).show(); return; }
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            final List<BluetoothDevice> devs = new ArrayList<>(bonded);
            if (devs.isEmpty()) { Toast.makeText(this, "Pair the other phone in Settings first", Toast.LENGTH_LONG).show(); return; }
            String[] names = new String[devs.size()];
            for (int i = 0; i < devs.size(); i++) names[i] = devs.get(i).getName() + "\n" + devs.get(i).getAddress();
            new AlertDialog.Builder(this)
                    .setTitle("Pick the receiver phone")
                    .setItems(names, (d, i) -> {
                        BluetoothDevice dev = devs.get(i);
                        Prefs.setReceiverBt(this, dev.getAddress(), dev.getName());
                        btSel.setText(btLabel());
                    })
                    .show();
        } catch (Throwable t) {
            Toast.makeText(this, "Bluetooth error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String btLabel() {
        String n = Prefs.receiverBtName(this), m = Prefs.receiverBt(this);
        return m.isEmpty() ? "No receiver device picked" : "Receiver device: " + n + " (" + m + ")";
    }

    private void refreshStatus() {
        StringBuilder s = new StringBuilder();
        s.append("\nThis phone (for the Sender to point at):\n");
        for (String ip : ipv4()) s.append("  ").append(ip).append(":").append(Config.PORT).append("\n");
        s.append("Port ").append(Config.PORT).append(" · token ").append(Config.TOKEN).append("\n");
        int role = Prefs.role(this);
        s.append("\nRole: ").append(role == Prefs.RECEIVER ? "Receiver" : role == Prefs.SENDER ? "Sender" : "Off");
        s.append(" · WiFi ").append(Prefs.wifi(this) ? "on" : "off");
        s.append(" · Bluetooth ").append(Prefs.bt(this) ? "on" : "off").append("\n");
        if (role == Prefs.RECEIVER)
            s.append("Receiver: enable this app in LSPosed (scope com.android.phone) and reboot.\n");
        s.append("\nWatch the LSPosed log (tag SMSLink) on the receiver when a text arrives.\n");
        if (BuildConfig.DEBUG)
            s.append("Debug build: also writes a log. Find it with root: su -c 'find /data -name smslink.log'\n");
        status.setText(s);
    }

    // ---- helpers ----
    private boolean has(String perm) { return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED; }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private TextView label(String text, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private RadioButton radio(String text, int role) {
        RadioButton r = new RadioButton(this);
        r.setText(text);
        r.setId(100 + role);
        return r;
    }

    private List<String> ipv4() {
        List<String> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address) r.add(a.getHostAddress());
            }
        } catch (Throwable ignored) {}
        if (r.isEmpty()) r.add("<no WiFi address>");
        return r;
    }
}
