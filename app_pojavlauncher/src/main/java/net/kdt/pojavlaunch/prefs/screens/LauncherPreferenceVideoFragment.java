package net.kdt.pojavlaunch.prefs.screens;

import android.content.SharedPreferences;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.RendererPluginRegistry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fragment for any settings video related
 */
public class LauncherPreferenceVideoFragment extends LauncherPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_video);
        int resolution = (int) (LauncherPreferences.PREF_SCALE_FACTOR * 100);

        //Disable notch checking behavior on android 8.1 and below.
        requirePreference("ignoreNotch").setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && LauncherPreferences.PREF_NOTCH_SIZE > 0);

        configureRendererPreference();

        CustomSeekBarPreference resolutionSeekbar = requirePreference("resolutionRatio",
                CustomSeekBarPreference.class);
        resolutionSeekbar.setSuffix(" %");

        // #724 bug fix
        if (resolution < 25) {
            resolutionSeekbar.setValue(100);
        } else {
            resolutionSeekbar.setValue(resolution);
        }

        // Sustained performance is only available since Nougat
        SwitchPreference sustainedPerfSwitch = requirePreference("sustainedPerformance",
                SwitchPreference.class);
        sustainedPerfSwitch.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N);
        sustainedPerfSwitch.setChecked(LauncherPreferences.PREF_SUSTAINED_PERFORMANCE);

        requirePreference("alternate_surface", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE);
        requirePreference("force_vsync", SwitchPreferenceCompat.class).setChecked(LauncherPreferences.PREF_FORCE_VSYNC);

        computeVisibility();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        computeVisibility();
    }

    private void computeVisibility(){
        requirePreference("force_vsync", SwitchPreferenceCompat.class)
                .setVisible(true);
    }

    private void configureRendererPreference() {
        ListPreference rendererPreference = requirePreference("renderer", ListPreference.class);
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();

        entries.add(getString(R.string.preference_renderer_auto));
        values.add("auto");

        Set<String> compatibleRenderers = new HashSet<>(Tools.getCompatibleRenderers(requireContext()).rendererIds);
        String[] rendererDisplayNames = getResources().getStringArray(R.array.renderer);
        String[] rendererValues = getResources().getStringArray(R.array.renderer_values);
        for (int i = 0; i < rendererValues.length && i < rendererDisplayNames.length; i++) {
            String rendererValue = rendererValues[i];
            String rendererDisplayName = rendererDisplayNames[i];
            if (!compatibleRenderers.contains(rendererValue)) {
                rendererDisplayName = getString(R.string.renderer_unavailable_entry, rendererDisplayName);
            }
            entries.add(rendererDisplayName);
            values.add(rendererValue);
        }
        for (RendererPluginRegistry.Entry entry : RendererPluginRegistry.load(requireContext())) {
            if (values.contains(entry.id) || values.contains(entry.runtimeRenderer)) {
                continue;
            }
            String rendererDisplayName = entry.name;
            if (!compatibleRenderers.contains(entry.id)) {
                rendererDisplayName = getString(R.string.renderer_unavailable_entry, rendererDisplayName);
            }
            entries.add(rendererDisplayName);
            values.add(entry.id);
        }

        rendererPreference.setEntries(entries.toArray(new CharSequence[0]));
        rendererPreference.setEntryValues(values.toArray(new CharSequence[0]));
        rendererPreference.setOnPreferenceClickListener(preference -> {
            showRendererDialog(rendererPreference, entries, values, compatibleRenderers);
            return true;
        });

        String currentValue = rendererPreference.getValue();
        if (currentValue == null || !values.contains(currentValue)) {
            rendererPreference.setValue("auto");
        }
    }

    private void showRendererDialog(ListPreference rendererPreference,
                                    List<CharSequence> entries,
                                    List<CharSequence> values,
                                    Set<String> compatibleRenderers) {
        String currentValue = rendererPreference.getValue();
        int checkedIndex = Math.max(0, values.indexOf(currentValue));

        Dialog dialog = new Dialog(requireContext());
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(18));
        root.setBackgroundResource(R.drawable.bg_battly_form_panel);

        TextView title = new TextView(requireContext());
        title.setText(rendererPreference.getTitle());
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView subtitle = new TextView(requireContext());
        subtitle.setText(R.string.renderer_dialog_subtitle);
        subtitle.setTextColor(Color.rgb(199, 212, 223));
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        root.addView(subtitle, subtitleParams);

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        LinearLayout list = new LinearLayout(requireContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(8));
        scrollView.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f);
        scrollParams.topMargin = dp(10);
        root.addView(scrollView, scrollParams);

        for (int i = 0; i < entries.size(); i++) {
            final int index = i;
            String selectedValue = values.get(index).toString();
            boolean isAvailable = "auto".equals(selectedValue) || compatibleRenderers.contains(selectedValue);
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(4), dp(14), dp(4));
            row.setBackgroundResource(index == checkedIndex
                    ? R.drawable.bg_battly_launcher_play
                    : R.drawable.bg_battly_button_secondary);
            row.setAlpha(isAvailable ? 1f : 0.58f);

            RadioButton radioButton = new RadioButton(requireContext());
            radioButton.setClickable(false);
            radioButton.setChecked(index == checkedIndex);
            row.addView(radioButton, new LinearLayout.LayoutParams(dp(36), dp(36)));

            TextView label = new TextView(requireContext());
            label.setText(entries.get(index));
            label.setTextColor(Color.WHITE);
            label.setTextSize(15);
            label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
            row.addView(label, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f));

            row.setOnClickListener(v -> {
                String value = values.get(index).toString();
                if (!"auto".equals(value) && !compatibleRenderers.contains(value)) {
                    Toast.makeText(requireContext(), R.string.renderer_unavailable_selected, Toast.LENGTH_LONG).show();
                    return;
                }
                rendererPreference.setValue(value);
                dialog.dismiss();
            });

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.bottomMargin = dp(6);
            list.addView(row, rowParams);
        }

        Button cancel = new Button(requireContext());
        cancel.setText(android.R.string.cancel);
        cancel.setAllCaps(false);
        cancel.setTextColor(Color.WHITE);
        cancel.setTextSize(14);
        cancel.setBackgroundResource(R.drawable.bg_battly_button_secondary);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(170), dp(48));
        cancelParams.gravity = Gravity.END;
        root.addView(cancel, cancelParams);

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnShowListener(d -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                WindowManager.LayoutParams params = new WindowManager.LayoutParams();
                params.copyFrom(shownWindow.getAttributes());
                params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
                params.height = (int) (getResources().getDisplayMetrics().heightPixels * 0.86f);
                params.gravity = Gravity.CENTER;
                shownWindow.setAttributes(params);
                shownWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
