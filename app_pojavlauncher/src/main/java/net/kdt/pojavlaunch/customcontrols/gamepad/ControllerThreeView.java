package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.InputDevice;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;
import java.util.Collections;

/** Local WebGL photo controller preview. It never loads remote content. */
public class ControllerThreeView extends WebView {
    private boolean mReady;
    private ControllerTypeResolver.Style mControllerStyle = ControllerTypeResolver.Style.XBOX;
    private boolean mDualShock;
    private String mHighlighted = "";
    private int mApplyAttempts;

    public ControllerThreeView(Context context) {
        this(context, null);
    }

    public ControllerThreeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.TRANSPARENT);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"appassets.battly.local".equals(uri.getHost())) return null;
                String path = uri.getPath();
                if (path == null || !path.startsWith("/controller3d/")) return null;
                String assetPath = path.substring(1);
                try {
                    WebResourceResponse response = new WebResourceResponse(
                            mimeType(assetPath),
                            null,
                            getContext().getAssets().open(assetPath));
                    response.setResponseHeaders(Collections.singletonMap(
                            "Access-Control-Allow-Origin", "*"));
                    return response;
                } catch (Exception ignored) {
                    return null;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mReady = true;
                applyState();
            }
        });
        loadUrl("https://appassets.battly.local/controller3d/index.html");
    }

    public void setControllerStyle(ControllerTypeResolver.Style style) {
        mControllerStyle = style == null || style == ControllerTypeResolver.Style.AUTO
                ? ControllerTypeResolver.Style.XBOX : style;
        applyState();
    }

    public void setControllerDevice(InputDevice device) {
        String name = device == null ? "" : device.getName().toLowerCase(Locale.ROOT);
        mDualShock = name.contains("dualshock") || name.contains("dual shock");
        applyState();
    }

    public void highlightControl(String control) {
        mHighlighted = control == null ? "" : control;
        applyState();
    }

    private String styleKey() {
        if (mControllerStyle == ControllerTypeResolver.Style.PLAYSTATION) {
            return mDualShock ? "dualshock" : "dualsense";
        }
        if (mControllerStyle == ControllerTypeResolver.Style.SWITCH) return "switch";
        return "xbox";
    }

    private void applyState() {
        if (!mReady) return;
        String script = "(function(){if(!window.BattlyControllerReady)return false;"
                + "window.setControllerStyle('" + styleKey() + "');"
                + "window.highlightControl('" + mHighlighted + "',true);return true;})()";
        evaluateJavascript(script, result -> {
            if ("true".equals(result)) {
                mApplyAttempts = 0;
            } else if (mApplyAttempts++ < 30 && isAttachedToWindow()) {
                postDelayed(this::applyState, 100);
            }
        });
    }

    private static String mimeType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".glb")) return "model/gltf-binary";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }
}
