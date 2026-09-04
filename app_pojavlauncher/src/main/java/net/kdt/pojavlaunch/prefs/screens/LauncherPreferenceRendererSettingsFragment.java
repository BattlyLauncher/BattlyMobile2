package net.kdt.pojavlaunch.prefs.screens;

import static android.text.InputType.TYPE_CLASS_NUMBER;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.MobileGluesBenchmarkResult;
import net.kdt.pojavlaunch.utils.MobileGluesBenchmarkRunner;
import net.kdt.pojavlaunch.utils.RendererPluginRegistry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class LauncherPreferenceRendererSettingsFragment extends LauncherPreferenceFragment {
    private static final Map<String, String> BENCHMARK_PREFERENCES = new LinkedHashMap<>();

    static {
        BENCHMARK_PREFERENCES.put("glMultiDrawArrays", "mg_renderer_multidraw_arrays");
        BENCHMARK_PREFERENCES.put("glMultiDrawElements", "mg_renderer_multidraw_elements");
        BENCHMARK_PREFERENCES.put("glMultiDrawElementsBaseVertex", "mg_renderer_multidraw_elements_base_vertex");
        BENCHMARK_PREFERENCES.put("glMultiDrawArraysIndirect", "mg_renderer_multidraw_arrays_indirect");
        BENCHMARK_PREFERENCES.put("glMultiDrawElementsIndirect", "mg_renderer_multidraw_elements_indirect");
    }

    EditTextPreference GLSLCachePreference;
    private MobileGluesBenchmarkRunner benchmarkRunner;
    private AlertDialog benchmarkDialog;
    private ProgressBar benchmarkProgress;
    private TextView benchmarkStatus;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_renderer);
        GLSLCachePreference = findPreference("mg_renderer_setting_glsl_cache_size");
        GLSLCachePreference.setOnBindEditTextListener((editText) -> {
            editText.setInputType(TYPE_CLASS_NUMBER);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Nothing, its boilerplate
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    // Nothing, its boilerplate
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // This is just to handle the summary not updating when its above max int value
                    // Horrible I know.
                    if (editText.getText().toString().isEmpty()) {
                        editText.setText("0");
                    }
                    if (Long.parseLong(editText.getText().toString()) > Integer.MAX_VALUE) {
                        editText.setError("Too big! Setting to maximum value");
                        editText.setText(String.valueOf(Integer.MAX_VALUE));
                    }

                }
            });
        });
        updateGLSLCacheSummary(); // Just updates the summary with the value when user opens the menu. Yes it's out of place.
        Preference benchmark = findPreference("mg_renderer_benchmark");
        if (benchmark != null) benchmark.setOnPreferenceClickListener(preference -> {
            confirmBenchmark();
            return true;
        });
        populateRendererStatus();
    }

    private void confirmBenchmark() {
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.mg_benchmark_title)
                .setMessage(R.string.mg_benchmark_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mg_benchmark_start, (dialog, which) -> startBenchmark()));
    }

    private void startBenchmark() {
        if (benchmarkRunner != null) return;
        try {
            LauncherPreferences.writeMGRendererSettings(false);
        } catch (IOException | RuntimeException exception) {
            Tools.dialog(requireContext(), getString(R.string.mg_benchmark_result_title),
                    getString(R.string.mg_benchmark_failed, exception.getMessage()));
            return;
        }

        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        showBenchmarkProgress();
        benchmarkRunner = new MobileGluesBenchmarkRunner(requireContext(),
                new MobileGluesBenchmarkRunner.Listener() {
                    @Override
                    public void onProgress(MobileGluesBenchmarkRunner.Progress progress) {
                        if (!isAdded() || benchmarkProgress == null || benchmarkStatus == null) return;
                        int percent = Math.max(0, Math.min(100, Math.round(progress.fraction * 100f)));
                        benchmarkProgress.setIndeterminate(false);
                        benchmarkProgress.setProgress(percent);
                        benchmarkStatus.setText(getString(
                                R.string.mg_benchmark_attempt, progress.attempt, percent));
                    }

                    @Override
                    public void onRetry(int sections) {
                        if (!isAdded() || benchmarkStatus == null) return;
                        benchmarkStatus.setText(getString(R.string.mg_benchmark_retry, sections));
                        if (benchmarkProgress != null) benchmarkProgress.setIndeterminate(true);
                    }

                    @Override
                    public void onComplete(MobileGluesBenchmarkResult result) {
                        finishBenchmarkUi();
                        if (!isAdded()) return;
                        if (result.isSuccessful()) showBenchmarkResult(result);
                        else showBenchmarkFailure(result.getError());
                    }

                    @Override
                    public void onFailure(String message) {
                        finishBenchmarkUi();
                        if (isAdded()) showBenchmarkFailure(message);
                    }
                });
        benchmarkRunner.start();
    }

    private void showBenchmarkProgress() {
        int padding = (int) Tools.dpToPx(20);
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 2, padding, padding / 2);

        benchmarkStatus = new TextView(requireContext());
        benchmarkStatus.setText(R.string.mg_benchmark_running);
        benchmarkStatus.setTextSize(15f);
        benchmarkStatus.setTextColor(0xFFE7EEF4);
        content.addView(benchmarkStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        benchmarkProgress = new ProgressBar(
                requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        benchmarkProgress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) Tools.dpToPx(8));
        progressParams.topMargin = (int) Tools.dpToPx(16);
        content.addView(benchmarkProgress, progressParams);

        benchmarkDialog = Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.mg_benchmark_title)
                .setView(content)
                .setNegativeButton(R.string.mg_benchmark_cancel, (dialog, which) -> cancelBenchmark()));
        benchmarkDialog.setCanceledOnTouchOutside(false);
        benchmarkDialog.setOnCancelListener(dialog -> cancelBenchmark());
    }

    private void showBenchmarkResult(MobileGluesBenchmarkResult result) {
        StringBuilder message = new StringBuilder();
        String renderer = result.getRenderer() == null ? "MobileGlues" : result.getRenderer();
        message.append(getString(R.string.mg_benchmark_result_intro,
                result.getElapsedMs() / 1000.0, renderer));

        for (Map.Entry<String, String> mapping : BENCHMARK_PREFERENCES.entrySet()) {
            List<String> ranking = result.getRanking(mapping.getKey());
            if (ranking.isEmpty()) continue;
            message.append("\n\n").append(functionLabel(mapping.getKey())).append('\n');
            for (int index = 0; index < ranking.size(); index++) {
                message.append(index + 1).append(". ").append(backendLabel(ranking.get(index))).append('\n');
            }
            MobileGluesBenchmarkResult.Quality quality = result.getQuality(mapping.getKey());
            if (quality != null) {
                message.append(getString(R.string.mg_benchmark_quality,
                        quality.rounds, quality.noise * 100.0));
            }
        }
        if (result.hasNoisyEntries()) {
            message.append("\n\n").append(getString(R.string.mg_benchmark_noisy));
        }
        if (result.hasDriverMismatch()) {
            message.append("\n\n").append(getString(R.string.mg_benchmark_wrong_driver));
        }

        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.mg_benchmark_result_title)
                .setMessage(message.toString().trim())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.mg_benchmark_apply,
                        (dialog, which) -> applyBenchmarkResult(result)));
    }

    private void applyBenchmarkResult(MobileGluesBenchmarkResult result) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, String> mapping : BENCHMARK_PREFERENCES.entrySet()) {
            String value = result.getPreferenceValue(mapping.getKey());
            if (!value.isEmpty()) editor.putString(mapping.getValue(), value);
        }
        if (!editor.commit()) {
            showBenchmarkFailure(getString(R.string.mg_benchmark_save_failed));
            return;
        }
        try {
            LauncherPreferences.writeMGRendererSettings(false);
            Toast.makeText(requireContext(), R.string.mg_benchmark_applied, Toast.LENGTH_LONG).show();
        } catch (IOException | RuntimeException exception) {
            showBenchmarkFailure(exception.getMessage());
        }
    }

    private String functionLabel(String function) {
        switch (function) {
            case "glMultiDrawArrays": return getString(R.string.mg_renderer_multidraw_arrays);
            case "glMultiDrawElements": return getString(R.string.mg_renderer_multidraw_elements);
            case "glMultiDrawElementsBaseVertex": return getString(R.string.mg_renderer_multidraw_base_vertex);
            case "glMultiDrawArraysIndirect": return getString(R.string.mg_renderer_multidraw_arrays_indirect);
            case "glMultiDrawElementsIndirect": return getString(R.string.mg_renderer_multidraw_elements_indirect);
            default: return function;
        }
    }

    private String backendLabel(String backend) {
        switch (backend.toLowerCase(Locale.ROOT)) {
            case "multiarrays": return "MultiArrays";
            case "multibasevertex": return "MultiBaseVertex";
            case "multiindirect": return "MultiIndirect";
            case "basevertex": return "BaseVertex";
            case "indirect": return "Indirect";
            case "compute": return "Compute";
            case "unroll": return "Unroll";
            default: return backend;
        }
    }

    private void showBenchmarkFailure(String message) {
        Tools.dialog(requireContext(), getString(R.string.mg_benchmark_result_title),
                getString(R.string.mg_benchmark_failed,
                        message == null ? getString(R.string.mg_benchmark_not_measured) : message));
    }

    private void finishBenchmarkUi() {
        if (isAdded()) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        if (benchmarkDialog != null) benchmarkDialog.dismiss();
        benchmarkDialog = null;
        benchmarkProgress = null;
        benchmarkStatus = null;
        benchmarkRunner = null;
    }

    private void cancelBenchmark() {
        if (benchmarkRunner != null) benchmarkRunner.close();
        benchmarkRunner = null;
        if (isAdded()) requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onDestroy() {
        cancelBenchmark();
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        GLSLCachePreference = findPreference("mg_renderer_setting_glsl_cache_size");
        updateGLSLCacheSummary();
    }

    private void updateGLSLCacheSummary() {
        try {
            if (Objects.equals(Objects.requireNonNull(this.GLSLCachePreference).getText(), "") || Integer.parseInt(Objects.requireNonNull(this.GLSLCachePreference.getText())) == 0) {
                this.GLSLCachePreference.setSummary(getString(R.string.global_off));
            } else this.GLSLCachePreference.setSummary(this.GLSLCachePreference.getText() + " MB");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void populateRendererStatus() {
        if (getPreferenceScreen() == null) {
            return;
        }
        PreferenceCategory category = new PreferenceCategory(requireContext());
        category.setTitle(R.string.renderer_status_title);
        getPreferenceScreen().addPreference(category);

        String[] names = getResources().getStringArray(R.array.renderer);
        String[] values = getResources().getStringArray(R.array.renderer_values);
        Set<String> compatible = new HashSet<>();
        compatible.addAll(Tools.getCompatibleRenderers(requireContext()).rendererIds);
        for (int i = 0; i < names.length && i < values.length; i++) {
            String rendererId = values[i];
            boolean isCompatible = compatible.contains(rendererId);
            Preference preference = new Preference(requireContext());
            preference.setTitle(names[i]);
            preference.setSummary(getString(
                    isCompatible ? R.string.renderer_status_available : R.string.renderer_status_unavailable,
                    getRendererDescription(rendererId)
            ));
            preference.setEnabled(isCompatible);
            category.addPreference(preference);
        }
        for (RendererPluginRegistry.Entry entry : RendererPluginRegistry.load(requireContext())) {
            if (Arrays.asList(values).contains(entry.runtimeRenderer)) {
                continue;
            }
            boolean isCompatible = compatible.contains(entry.id);
            Preference preference = new Preference(requireContext());
            preference.setTitle(entry.name);
            preference.setSummary(getString(
                    isCompatible ? R.string.renderer_status_available : R.string.renderer_status_unavailable,
                    Tools.isValidString(entry.description) ? entry.description : getString(R.string.renderer_desc_generic)
            ));
            preference.setEnabled(isCompatible);
            category.addPreference(preference);
        }
    }

    private String getRendererDescription(String rendererId) {
        if ("opengles2".equals(rendererId)) {
            return getString(R.string.renderer_desc_gl4es);
        }
        if ("opengles3_desktopgl_zink_kopper".equals(rendererId)) {
            return getString(R.string.renderer_desc_kopper);
        }
        if ("vulkan_zink".equals(rendererId)) {
            return getString(R.string.renderer_desc_zink);
        }
        if ("opengles_mobileglues".equals(rendererId)) {
            return getString(R.string.renderer_desc_mobileglues);
        }
        if ("opengles3_ltw".equals(rendererId)) {
            return getString(R.string.renderer_desc_ltw);
        }
        if ("opengles3_desktopgl_freedreno".equals(rendererId)) {
            GLInfoUtils.GLInfo info = GLInfoUtils.getGlInfo();
            if (!info.isAdreno()) {
                return getString(R.string.renderer_desc_freedreno_not_adreno, info.vendor + " " + info.renderer);
            }
            return getString(R.string.renderer_desc_freedreno);
        }
        return getString(R.string.renderer_desc_generic);
    }
}
