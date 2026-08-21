package net.kdt.pojavlaunch.battlyworlds;

import android.content.Context;
import android.util.Log;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BattlyWorldsNodeList {
    private static final String TAG = "BattlyWorldsNodeList";
    private static final String NODE_LIST_URL = "https://api.battlylauncher.com/battlylauncher/battlyworlds/nodes?platform=android";
    private static final List<String> FALLBACK_NODES = Arrays.asList(
            "tcp://node-eu-1.battlylauncher.com:11010",
            "udp://node-eu-1.battlylauncher.com:11010",
            "ws://node-eu-1.battlylauncher.com:11011",
            "tcp://node-us-1.battlylauncher.com:11010",
            "udp://node-us-1.battlylauncher.com:11010",
            "ws://node-us-1.battlylauncher.com:11011"
    );

    private static volatile List<String> sCachedNodes;

    public static final class ProbeResult {
        public final String node;
        public final long latencyMs;
        public final boolean reachable;
        public final String message;

        ProbeResult(String node, long latencyMs, boolean reachable, String message) {
            this.node = node == null ? "" : node;
            this.latencyMs = latencyMs;
            this.reachable = reachable;
            this.message = message == null ? "" : message;
        }
    }

    public static List<String> fetch() {
        return fetch(null);
    }

    public static List<String> fetch(Context context) {
        boolean plus = context != null && BattlyWorldsInvites.getCachedEntitlements().plus;
        List<String> cached = sCachedNodes;
        if (!plus && cached != null && !cached.isEmpty()) {
            return cached;
        }

        synchronized (BattlyWorldsNodeList.class) {
            cached = sCachedNodes;
            if (!plus && cached != null && !cached.isEmpty()) {
                return cached;
            }

            try {
                String urlString = plus ? NODE_LIST_URL + "&premium=1" : NODE_LIST_URL;
                String raw = DownloadUtils.downloadStringFreshWithCacheFallback(
                        urlString,
                        plus ? "battlyworlds_nodes_plus.json" : "battlyworlds_nodes.json",
                        input -> input
                );
                JsonArray array = Tools.GLOBAL_GSON.fromJson(raw, JsonArray.class);
                List<String> nodes = new ArrayList<>();
                for (JsonElement element : array) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    if (!object.has("url")) {
                        continue;
                    }
                    String url = object.get("url").getAsString();
                    if (isValidNodeUrl(url)) {
                        nodes.add(url);
                    }
                }
                if (!nodes.isEmpty()) {
                    if (!plus) {
                        sCachedNodes = nodes;
                    }
                    return nodes;
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "Unable to fetch BattlyWorlds node list, using fallback nodes", throwable);
            }

            sCachedNodes = FALLBACK_NODES;
            return FALLBACK_NODES;
        }
    }

    public static ProbeResult probe(Context context) {
        List<String> nodes = fetch(context);
        String lastError = "No compatible node available";
        for (String node : nodes) {
            try {
                URI uri = URI.create(node);
                if ("udp".equals(uri.getScheme()) || "wg".equals(uri.getScheme())
                        || "quic".equals(uri.getScheme())) {
                    continue;
                }
                long started = System.nanoTime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(uri.getHost(), uri.getPort()), 4500);
                }
                long latency = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                return new ProbeResult(node, latency, true, "");
            } catch (Throwable throwable) {
                lastError = throwable.getMessage();
            }
        }
        String node = nodes.isEmpty() ? "" : nodes.get(0);
        return new ProbeResult(node, -1L, false, lastError);
    }

    private static boolean isValidNodeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && uri.getPort() > 0
                    && ("tcp".equals(scheme)
                    || "udp".equals(scheme)
                    || "ws".equals(scheme)
                    || "wss".equals(scheme)
                    || "wg".equals(scheme)
                    || "quic".equals(scheme));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private BattlyWorldsNodeList() {
    }
}
