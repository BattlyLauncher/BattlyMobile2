package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import org.json.JSONObject;

import java.util.Map;

public final class BattlyInAppMessaging {
    public static final String ACTION_SHOW_IN_APP_MESSAGE = "net.kdt.pojavlaunch.action.BATTLY_IN_APP_MESSAGE";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_BODY = "body";
    public static final String EXTRA_ACTION_TEXT = "action_text";
    public static final String EXTRA_ACTION_URL = "action_url";

    private static final String PREFS = "battly_in_app_messages";
    private static final String PREF_PENDING = "pending_message";

    private BattlyInAppMessaging() {
    }

    public static boolean isInAppMessage(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        String type = first(data, "type", "message_type", "notification_type", "kind");
        if (type != null) {
            String normalized = type.trim().toLowerCase();
            if ("in_app".equals(normalized) || "inapp".equals(normalized)
                    || "in-app".equals(normalized) || "battly_in_app".equals(normalized)) {
                return true;
            }
        }
        return isTruthy(first(data, "in_app", "inapp", "show_in_app"));
    }

    public static void dispatch(Context context, Map<String, String> data, String fallbackTitle, String fallbackBody) {
        if (context == null) {
            return;
        }
        Intent intent = createIntent(context, data, fallbackTitle, fallbackBody);
        savePending(context, intent);
        context.sendBroadcast(intent);
    }

    public static void showFromIntent(Activity activity, Intent intent) {
        if (activity == null || intent == null || activity.isFinishing()) {
            return;
        }
        String title = value(intent.getStringExtra(EXTRA_TITLE), activity.getString(R.string.battly_in_app_title));
        String body = value(intent.getStringExtra(EXTRA_BODY), "");
        String actionText = value(intent.getStringExtra(EXTRA_ACTION_TEXT), activity.getString(R.string.battly_in_app_open));
        String actionUrl = value(intent.getStringExtra(EXTRA_ACTION_URL), "");

        AlertDialog.Builder builder = Tools.createStyledDialogBuilder(activity)
                .setTitle(title)
                .setMessage(body)
                .setNegativeButton(android.R.string.ok, null);
        if (Tools.isValidString(actionUrl)) {
            builder.setPositiveButton(actionText, (dialog, which) -> {
                Intent openIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl));
                activity.startActivity(openIntent);
            });
        }
        Tools.showStyledDialog(builder);
        clearPending(activity);
    }

    public static void showPendingIfAny(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        String payload = prefs(activity).getString(PREF_PENDING, null);
        if (!Tools.isValidString(payload)) {
            return;
        }
        try {
            JSONObject object = new JSONObject(payload);
            Intent intent = new Intent(ACTION_SHOW_IN_APP_MESSAGE);
            intent.putExtra(EXTRA_TITLE, object.optString(EXTRA_TITLE));
            intent.putExtra(EXTRA_BODY, object.optString(EXTRA_BODY));
            intent.putExtra(EXTRA_ACTION_TEXT, object.optString(EXTRA_ACTION_TEXT));
            intent.putExtra(EXTRA_ACTION_URL, object.optString(EXTRA_ACTION_URL));
            showFromIntent(activity, intent);
        } catch (Exception ignored) {
            clearPending(activity);
        }
    }

    private static Intent createIntent(Context context, Map<String, String> data, String fallbackTitle, String fallbackBody) {
        String title = value(first(data, "title", "subject"), fallbackTitle);
        String body = value(first(data, "body", "message", "text", "content"), fallbackBody);
        String actionText = value(first(data, "action_text", "button", "button_text", "cta"), "");
        String actionUrl = value(first(data, "action_url", "url", "link", "cta_url"), "");

        Intent intent = new Intent(ACTION_SHOW_IN_APP_MESSAGE);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_BODY, body);
        intent.putExtra(EXTRA_ACTION_TEXT, actionText);
        intent.putExtra(EXTRA_ACTION_URL, actionUrl);
        return intent;
    }

    private static void savePending(Context context, Intent intent) {
        try {
            JSONObject object = new JSONObject();
            object.put(EXTRA_TITLE, value(intent.getStringExtra(EXTRA_TITLE), ""));
            object.put(EXTRA_BODY, value(intent.getStringExtra(EXTRA_BODY), ""));
            object.put(EXTRA_ACTION_TEXT, value(intent.getStringExtra(EXTRA_ACTION_TEXT), ""));
            object.put(EXTRA_ACTION_URL, value(intent.getStringExtra(EXTRA_ACTION_URL), ""));
            prefs(context).edit().putString(PREF_PENDING, object.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void clearPending(Context context) {
        prefs(context).edit().remove(PREF_PENDING).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String first(Map<String, String> data, String... keys) {
        if (data == null) {
            return null;
        }
        for (String key : keys) {
            String value = data.get(key);
            if (Tools.isValidString(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isTruthy(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }

    private static String value(String value, String fallback) {
        return Tools.isValidString(value) ? value : fallback;
    }
}
