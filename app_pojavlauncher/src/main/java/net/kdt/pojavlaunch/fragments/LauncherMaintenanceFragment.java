package net.kdt.pojavlaunch.fragments;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.BattlyComponentUpdater;
import net.kdt.pojavlaunch.utils.BattlyRepairManager;
import net.kdt.pojavlaunch.utils.GraphicsAdvisor;
import net.kdt.pojavlaunch.views.TouchCalibrationView;

import java.util.List;

public class LauncherMaintenanceFragment extends Fragment {
    public LauncherMaintenanceFragment() {
        super(R.layout.fragment_launcher_maintenance);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        bindCard(view, R.id.maintenance_graphics_card, R.id.maintenance_graphics_status,
                this::runGraphicsAdvisor);
        bindCard(view, R.id.maintenance_repair_card, R.id.maintenance_repair_status,
                this::confirmRepair);
        bindCard(view, R.id.maintenance_touch_card, R.id.maintenance_touch_status,
                this::showTouchCalibration);
        bindCard(view, R.id.maintenance_components_card, R.id.maintenance_components_status,
                this::updateComponents);
        updateTouchStatus(view.findViewById(R.id.maintenance_touch_status));
    }

    private void bindCard(View root, int cardId, int statusId, Runnable action) {
        View card = root.findViewById(cardId);
        TextView status = root.findViewById(statusId);
        card.setOnClickListener(v -> {
            if (!card.isEnabled()) return;
            action.run();
        });
        card.setTag(R.id.maintenance_status_tag, status);
    }

    private void runGraphicsAdvisor() {
        View card = requireView().findViewById(R.id.maintenance_graphics_card);
        setBusy(card, true, getString(R.string.maintenance_testing));
        PojavApplication.sExecutorService.execute(() -> {
            try {
                GraphicsAdvisor.Result result = GraphicsAdvisor.run(requireContext().getApplicationContext());
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    setBusy(card, false, getString(R.string.maintenance_graphics_ready,
                            friendlyRenderer(result.recommendedRenderer)));
                    String message = getString(R.string.maintenance_graphics_result,
                            result.versionId,
                            friendlyRenderer(result.recommendedRenderer),
                            result.deviceSummary(),
                            result.benchmarkMs);
                    Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(R.string.maintenance_graphics_title)
                            .setMessage(message)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.maintenance_apply, (dialog, which) -> {
                                GraphicsAdvisor.apply(result.recommendedRenderer);
                                Toast.makeText(requireContext(), R.string.maintenance_applied, Toast.LENGTH_LONG).show();
                            }));
                });
            } catch (Throwable throwable) {
                showFailure(card, throwable);
            }
        });
    }

    private void confirmRepair() {
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.maintenance_repair_title)
                .setMessage(R.string.maintenance_repair_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.maintenance_repair_action, (dialog, which) -> runRepair()));
    }

    private void runRepair() {
        View card = requireView().findViewById(R.id.maintenance_repair_card);
        setBusy(card, true, getString(R.string.maintenance_repairing));
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlyRepairManager.Result result = BattlyRepairManager.repair(
                        requireContext().getApplicationContext(),
                        (progress, stage) -> Tools.runOnUiThread(() -> {
                            if (isAdded()) setStatus(card, progress + "% · " + stage);
                        }));
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    setBusy(card, false, getString(result.isSuccessful()
                            ? R.string.maintenance_repair_complete
                            : R.string.maintenance_repair_warning));
                    Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                            .setTitle(R.string.maintenance_repair_result_title)
                            .setMessage(buildRepairResult(result))
                            .setPositiveButton(android.R.string.ok, null));
                });
            } catch (Throwable throwable) {
                showFailure(card, throwable);
            }
        });
    }

    private String buildRepairResult(BattlyRepairManager.Result result) {
        StringBuilder builder = new StringBuilder();
        builder.append(getString(R.string.maintenance_repair_result_summary,
                result.actions.size(), result.temporaryFilesRemoved + result.corruptFilesRemoved));
        appendLines(builder, result.actions);
        if (!result.warnings.isEmpty()) {
            builder.append("\n\n").append(getString(R.string.maintenance_warnings));
            appendLines(builder, result.warnings);
        }
        return builder.toString();
    }

    private void updateComponents() {
        View card = requireView().findViewById(R.id.maintenance_components_card);
        setBusy(card, true, getString(R.string.maintenance_components_checking));
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlyComponentUpdater.Result result = BattlyComponentUpdater.updateAll(
                        requireContext().getApplicationContext(),
                        (current, total, component) -> Tools.runOnUiThread(() -> {
                            if (isAdded()) setStatus(card, getString(
                                    R.string.maintenance_components_progress, current, Math.max(total, 1)));
                        }));
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    setBusy(card, false, getString(R.string.maintenance_components_result,
                            result.updated.size(), result.current.size()));
                    Toast.makeText(requireContext(), getString(R.string.maintenance_components_result,
                            result.updated.size(), result.current.size()), Toast.LENGTH_LONG).show();
                });
            } catch (Throwable throwable) {
                showFailure(card, throwable);
            }
        });
    }

    private void showTouchCalibration() {
        LauncherPreferences.loadPreferences(requireContext());
        final int original = Math.round(LauncherPreferences.PREF_TOUCHSCREEN_SENSITIVITY * 100f);
        Dialog dialog = new Dialog(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(18));
        root.setBackgroundResource(R.drawable.bg_battly_form_panel);

        TextView title = text(getString(R.string.maintenance_touch_title), 22, Color.WHITE, true);
        root.addView(title);
        TextView description = text(getString(R.string.maintenance_touch_instruction), 13,
                Color.rgb(199, 212, 223), false);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(4);
        root.addView(description, descriptionParams);

        TouchCalibrationView test = new TouchCalibrationView(requireContext());
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        testParams.topMargin = dp(14);
        root.addView(test, testParams);

        TextView feedback = text(getString(R.string.maintenance_touch_waiting), 13,
                Color.rgb(142, 210, 229), true);
        LinearLayout.LayoutParams feedbackParams = matchWrap();
        feedbackParams.topMargin = dp(8);
        root.addView(feedback, feedbackParams);

        TextView value = text(original + "%", 16, Color.WHITE, true);
        LinearLayout.LayoutParams valueParams = matchWrap();
        valueParams.topMargin = dp(8);
        root.addView(value, valueParams);

        SeekBar sensitivity = new SeekBar(requireContext());
        sensitivity.setMax(390);
        sensitivity.setProgress(Math.max(0, Math.min(390, original - 10)));
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText((progress + 10) + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        root.addView(sensitivity, matchWrap());
        test.setListener((samples, distancePx) -> feedback.setText(samples < 8
                ? getString(R.string.maintenance_touch_waiting)
                : getString(R.string.maintenance_touch_samples, samples, Math.round(distancePx))));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button reset = dialogButton(R.string.maintenance_reset);
        reset.setOnClickListener(v -> {
            sensitivity.setProgress(90);
            test.reset();
        });
        Button cancel = dialogButton(android.R.string.cancel);
        cancel.setOnClickListener(v -> dialog.dismiss());
        Button save = dialogButton(R.string.maintenance_save);
        save.setBackgroundResource(R.drawable.bg_battly_launcher_play);
        save.setOnClickListener(v -> {
            int selected = sensitivity.getProgress() + 10;
            LauncherPreferences.DEFAULT_PREF.edit().putInt("touchscreenSensitivity", selected).apply();
            LauncherPreferences.loadPreferences(requireContext());
            updateTouchStatus(requireView().findViewById(R.id.maintenance_touch_status));
            dialog.dismiss();
        });
        actions.addView(reset, new LinearLayout.LayoutParams(dp(120), dp(46)));
        actions.addView(cancel, new LinearLayout.LayoutParams(dp(120), dp(46)));
        actions.addView(save, new LinearLayout.LayoutParams(dp(120), dp(46)));
        LinearLayout.LayoutParams actionsParams = matchWrap();
        actionsParams.topMargin = dp(10);
        root.addView(actions, actionsParams);

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
            params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    private void updateTouchStatus(TextView status) {
        LauncherPreferences.loadPreferences(requireContext());
        status.setText(getString(R.string.maintenance_touch_current,
                Math.round(LauncherPreferences.PREF_TOUCHSCREEN_SENSITIVITY * 100f)));
    }

    private void showFailure(View card, Throwable throwable) {
        Tools.runOnUiThread(() -> {
            if (!isAdded()) return;
            setBusy(card, false, getString(R.string.maintenance_failed));
            Tools.showError(requireContext(), throwable);
        });
    }

    private void setBusy(View card, boolean busy, String text) {
        card.setEnabled(!busy);
        card.setAlpha(busy ? 0.72f : 1f);
        setStatus(card, text);
    }

    private void setStatus(View card, String text) {
        Object tag = card.getTag(R.id.maintenance_status_tag);
        if (tag instanceof TextView) ((TextView) tag).setText(text);
    }

    private String friendlyRenderer(String id) {
        if ("opengles_mobileglues".equals(id)) return "MobileGlues";
        if ("opengles2".equals(id)) return "Holy GL4ES";
        if ("opengles3_desktopgl_freedreno".equals(id)) return "Freedreno";
        if ("opengles3_desktopgl_zink_kopper".equals(id)) return "Kopper Zink";
        return id == null ? getString(R.string.preference_renderer_auto) : id;
    }

    private void appendLines(StringBuilder builder, List<String> lines) {
        for (String line : lines) builder.append("\n• ").append(line);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button dialogButton(int textResource) {
        Button button = new Button(requireContext());
        button.setText(textResource);
        button.setTransformationMethod(null);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.bg_battly_button_secondary);
        return button;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
