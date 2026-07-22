package net.kdt.pojavlaunch.prefs.screens;

import static android.text.InputType.TYPE_CLASS_NUMBER;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.GLInfoUtils;
import net.kdt.pojavlaunch.utils.RendererPluginRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class LauncherPreferenceRendererSettingsFragment extends LauncherPreferenceFragment {
    EditTextPreference GLSLCachePreference;
    ListPreference MultiDrawEmulationPreference;
    SwitchPreference ComputeMultiDrawPreference;
    Preference.SummaryProvider MultiDrawSummaryProvider;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_renderer);
        GLSLCachePreference = findPreference("mg_renderer_setting_glsl_cache_size");
        ComputeMultiDrawPreference = findPreference("mg_renderer_multidrawCompute");
        MultiDrawEmulationPreference = findPreference("mg_renderer_setting_multidraw");
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
        updateMultiDrawSummary(); // Same as above
        populateRendererStatus();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        GLSLCachePreference = findPreference("mg_renderer_setting_glsl_cache_size");
        updateGLSLCacheSummary();
        updateMultiDrawSummary();
    }

    private void updateMultiDrawSummary() {
        if (MultiDrawEmulationPreference != null) {
            if (MultiDrawEmulationPreference.getSummaryProvider() != null) {
                MultiDrawSummaryProvider = MultiDrawEmulationPreference.getSummaryProvider();
            }
            if (ComputeMultiDrawPreference.isChecked()) {
                MultiDrawEmulationPreference.setEnabled(false);
                MultiDrawEmulationPreference.setSummaryProvider(null);
                MultiDrawEmulationPreference.setSummary("(Experimental) Compute");
            } else if (MultiDrawEmulationPreference != null) {
                MultiDrawEmulationPreference.setEnabled(true);
                MultiDrawEmulationPreference.setSummaryProvider(MultiDrawSummaryProvider);
            }
        }
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
