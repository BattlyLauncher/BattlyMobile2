package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public final class BattlyWorldsDiagnostics {
    public static void show(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 14), dp(activity, 8), dp(activity, 14), dp(activity, 8));

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint(R.string.battlyworlds_logs_search_hint);
        root.addView(search);

        TextView text = new TextView(activity);
        text.setTextIsSelectable(true);
        text.setTextColor(0xFFC6D6E3);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(12);
        text.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(text);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 280)));

        String collectedLogs = BattlyWorldsManager.collectLogs();
        String[] raw = {collectedLogs == null || collectedLogs.trim().isEmpty()
                ? activity.getString(R.string.battlyworlds_diagnostics_empty)
                : collectedLogs};
        Runnable refresh = () -> text.setText(filter(raw[0], search.getText().toString()));
        refresh.run();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_diagnostics_title)
                .setView(root)
                .setNegativeButton(R.string.global_cancel, null)
                .setNeutralButton(R.string.global_copy, null)
                .setPositiveButton(R.string.battlyworlds_logs_share, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> copy(activity, raw[0]));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> share(activity, raw[0]));
        });
        dialog.show();
    }

    private static String filter(String logs, String query) {
        String value = logs == null ? "" : logs;
        String needle = query == null ? "" : query.trim().toLowerCase();
        if (needle.isEmpty()) return value;
        StringBuilder result = new StringBuilder();
        for (String line : value.split("\n")) {
            if (line.toLowerCase().contains(needle)) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static void copy(Activity activity, String logs) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("BattlyWorlds logs", logs));
        Toast.makeText(activity, R.string.battlyworlds_copied, Toast.LENGTH_SHORT).show();
    }

    private static void share(Activity activity, String logs) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "BattlyWorlds diagnostics");
        intent.putExtra(Intent.EXTRA_TEXT, logs);
        activity.startActivity(Intent.createChooser(intent,
                activity.getString(R.string.battlyworlds_logs_share)));
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private BattlyWorldsDiagnostics() {
    }
}
