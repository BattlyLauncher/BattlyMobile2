package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import net.burningtnt.terracotta.TerracottaAndroidAPI;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Debug-only two-device probe for Terracotta's Scaffolding no-TUN transport. */
public final class TerracottaNoTunProbeActivity extends Activity {
    private static final String TAG = "TerracottaNoTunProbe";
    private static final String PROBE_ROOM = "U/V7HU-Z2G3-2897-Y6E7";
    private static final long DEADLINE_MS = 90_000L;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ServerSocket minecraftServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView output = new TextView(this);
        output.setPadding(32, 32, 32, 32);
        String role = getIntent().getStringExtra("role");
        role = "guest".equals(role) ? "guest" : "host";
        output.setText("Running Terracotta " + role + " probe...");
        setContentView(output);

        String finalRole = role;
        new Thread(() -> runProbe(output, finalRole), "Terracotta " + role + " probe").start();
    }

    private void runProbe(TextView output, String role) {
        AtomicBoolean tunRequested = new AtomicBoolean(false);
        String lastState = "not-started";
        Throwable failure = null;
        try {
            TerracottaAndroidAPI.Metadata metadata = TerracottaAndroidAPI.initialize(
                    getApplicationContext(),
                    () -> {
                        tunRequested.set(true);
                        try {
                            TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
                        } catch (Throwable throwable) {
                            Log.e(TAG, "Unable to reject unexpected TUN request", throwable);
                        }
                    });
            Log.i(TAG, "Terracotta=" + metadata.getTerracottaVersion()
                    + " EasyTier=" + metadata.getEasyTierVersion());

            List<String> nodes = BattlyWorldsNodeList.fetch(getApplicationContext());
            if ("host".equals(role)) {
                int port = startMinecraftProbeServer();
                startLanAdvertisement(port);
                TerracottaAndroidAPI.setScanning(PROBE_ROOM, "BattlyProbeHost", nodes);
            } else if (!TerracottaAndroidAPI.setGuesting(PROBE_ROOM, "BattlyProbeGuest", nodes)) {
                throw new IllegalStateException("Terracotta rejected the probe room code");
            }

            long deadline = System.currentTimeMillis() + DEADLINE_MS;
            String expected = "host".equals(role) ? "\"state\":\"host-ok\"" : "\"state\":\"guest-ok\"";
            while (System.currentTimeMillis() < deadline) {
                lastState = TerracottaAndroidAPI.getState();
                Log.i(TAG, role + "State=" + lastState);
                if (lastState.contains(expected)) break;
                if (lastState.contains("\"state\":\"exception\"")) {
                    throw new IllegalStateException(lastState);
                }
                Thread.sleep(500L);
            }
            if (!lastState.contains(expected)) {
                throw new IllegalStateException("Timed out waiting for " + expected + ": " + lastState);
            }
        } catch (Throwable throwable) {
            failure = throwable;
            Log.e(TAG, "Probe failed", throwable);
        }

        boolean passed = failure == null && !tunRequested.get();
        String result = "TERRACOTTA_" + role.toUpperCase() + " " + (passed ? "PASS" : "FAIL")
                + " tunRequested=" + tunRequested.get() + " state=" + lastState
                + (failure == null ? "" : " error=" + failure);
        Log.i(TAG, result);
        runOnUiThread(() -> output.setText(result));
    }

    private int startMinecraftProbeServer() throws Exception {
        minecraftServer = new ServerSocket(0);
        new Thread(() -> {
            while (running.get()) {
                try (Socket socket = minecraftServer.accept()) {
                    socket.setSoTimeout(5_000);
                    InputStream input = socket.getInputStream();
                    OutputStream output = socket.getOutputStream();
                    if (input.read() == 0xFE) {
                        output.write(0xFF);
                        output.flush();
                    }
                } catch (Throwable throwable) {
                    if (running.get()) Log.w(TAG, "Probe Minecraft server error", throwable);
                }
            }
        }, "Battly probe Minecraft server").start();
        return minecraftServer.getLocalPort();
    }

    private void startLanAdvertisement(int port) {
        new Thread(() -> {
            byte[] data = ("[MOTD]Battly Terracotta Probe[/MOTD][AD]" + port + "[/AD]")
                    .getBytes(StandardCharsets.UTF_8);
            while (running.get()) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    sendAdvertisement(socket, data, InetAddress.getByName("224.0.2.60"));
                    for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        for (InetAddress address : Collections.list(network.getInetAddresses())) {
                            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                                sendAdvertisement(socket, data, address);
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    Log.w(TAG, "Unable to advertise probe LAN server", throwable);
                }
                try {
                    Thread.sleep(1_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "Battly probe LAN advertiser").start();
    }

    private static void sendAdvertisement(DatagramSocket socket, byte[] data, InetAddress address) throws Exception {
        socket.send(new DatagramPacket(data, data.length, address, 4445));
    }

    @Override
    protected void onDestroy() {
        running.set(false);
        if (minecraftServer != null) {
            try {
                minecraftServer.close();
            } catch (Throwable ignored) {
            }
        }
        super.onDestroy();
    }
}
