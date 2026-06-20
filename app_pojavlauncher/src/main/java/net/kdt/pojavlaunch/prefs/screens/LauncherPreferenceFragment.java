package net.kdt.pojavlaunch.prefs.screens;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.CustomSeekBarPreference;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Preference for the main screen, any sub-screen should inherit this class for
 * consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String SETTINGS_HEADER_KEY = "settings_panel_header";
    private static final String BATTLY_PLUS_KEY = "battly_plus_screen";
    private static final int SETTINGS_SIDE_PADDING_DP = 24;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.setBackground(null);
        RecyclerView listView = getListView();
        listView.setClipToPadding(false);
        listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        listView.setPadding(dpToPx(SETTINGS_SIDE_PADDING_DP), dpToPx(8), dpToPx(SETTINGS_SIDE_PADDING_DP), dpToPx(28));
        stylePreferenceTree(getPreferenceScreen());
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_main);
        setupNotificationRequestPreference();

        Preference showOnboardingPref = findPreference("show_onboarding_again");
        if (showOnboardingPref != null) {
            showOnboardingPref.setOnPreferenceClickListener(preference -> {
                android.content.Intent intent = new android.content.Intent(requireContext(), net.kdt.pojavlaunch.onboarding.OnboardingActivity.class);
                startActivity(intent);
                return true;
            });
        }
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Preference mMicrophonePermissionPreference = requirePreference("microphone_permission_request");
        Activity activity = getActivity();
        if (activity instanceof LauncherActivity) {
            LauncherActivity launcherActivity = (LauncherActivity) activity;
            mRequestNotificationPermissionPreference.setVisible(!launcherActivity.checkForNotificationPermission());
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity
                        .askForNotificationPermission(() -> mRequestNotificationPermissionPreference.setVisible(false));
                return true;
            });
            mMicrophonePermissionPreference.setVisible(!launcherActivity.checkForMicrophonePermission());
            mMicrophonePermissionPreference.setOnPreferenceClickListener(preference -> {
                launcherActivity.askForMicrophonePermission(() -> mMicrophonePermissionPreference.setVisible(false));
                return true;
            });
        } else {
            mRequestNotificationPermissionPreference.setVisible(false);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null)
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if (sharedPreferences != null)
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if (preference != null)
            return preference;
        throw new IllegalStateException("Preference " + key + " is null");
    }

    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if (preferenceClass.isInstance(preference))
            return (T) preference;
        throw new IllegalStateException(
                "Preference " + key + " is not an instance of " + preferenceClass.getSimpleName());
    }

    @Override
    protected RecyclerView.Adapter<?> onCreateAdapter(PreferenceScreen preferenceScreen) {
        stylePreferenceTree(preferenceScreen);
        return new PreferenceGroupAdapter(preferenceScreen) {
            @Override
            public void onBindViewHolder(@NonNull PreferenceViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);
                Preference preference = getItem(position);
                if (preference != null) {
                    boolean isFirstInCategory = false;
                    boolean isLastInCategory = false;

                    if (!(preference instanceof PreferenceCategory) && !isSettingsHeader(preference)) {
                        Preference prev = position > 0 ? getItem(position - 1) : null;
                        Preference next = position < getItemCount() - 1 ? getItem(position + 1) : null;

                        isFirstInCategory = (prev == null || prev instanceof PreferenceCategory
                                || isSettingsHeader(prev));
                        isLastInCategory = (next == null || next instanceof PreferenceCategory
                                || isSettingsHeader(next));
                    }

                    stylePreferenceRow(holder, preference, isFirstInCategory, isLastInCategory);
                }
            }
        };
    }

    private void stylePreferenceTree(@Nullable Preference preference) {
        if (preference == null) {
            return;
        }

        if (preference instanceof PreferenceCategory) {
            preference.setLayoutResource(R.layout.preference_battly_category);
        } else if (isSettingsHeader(preference)) {
            preference.setLayoutResource(R.layout.preference_battly_header);
            preference.setSelectable(false);
            preference.setIconSpaceReserved(false);
        } else if (!(preference instanceof CustomSeekBarPreference)) {
            preference.setLayoutResource(R.layout.preference_battly_item);
            preference.setIconSpaceReserved(preference.getIcon() != null);
        } else {
            preference.setIconSpaceReserved(preference.getIcon() != null);
        }

        if (preference instanceof PreferenceGroup) {
            PreferenceGroup group = (PreferenceGroup) preference;
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                stylePreferenceTree(group.getPreference(i));
            }
        }
    }

    private void stylePreferenceRow(@NonNull PreferenceViewHolder holder, @NonNull Preference preference,
            boolean isFirst, boolean isLast) {
        View itemView = holder.itemView;
        ViewGroup.LayoutParams rawLayoutParams = itemView.getLayoutParams();
        RecyclerView.LayoutParams layoutParams = rawLayoutParams instanceof RecyclerView.LayoutParams
                ? (RecyclerView.LayoutParams) rawLayoutParams
                : new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView titleView = (TextView) holder.findViewById(android.R.id.title);
        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        View iconFrame = holder.findViewById(android.R.id.icon_frame);
        ImageView iconView = (ImageView) holder.findViewById(android.R.id.icon);

        if (preference instanceof PreferenceCategory) {
            itemView.setBackground(null);
            itemView.setPadding(0, 0, 0, 0);
            layoutParams.topMargin = dpToPx(24);
            layoutParams.bottomMargin = dpToPx(4);
            if (titleView != null) {
                titleView.setAllCaps(true);
                titleView.setLetterSpacing(0.05f);
                titleView.setTextColor(0xFF8ED2E5);
                titleView.setTextSize(13);
            }
            if (summaryView != null) {
                summaryView.setVisibility(View.GONE);
            }
        } else if (isSettingsHeader(preference)) {
            itemView.setBackground(null);
            itemView.setPadding(0, 0, 0, 0);
            layoutParams.topMargin = dpToPx(4);
            layoutParams.bottomMargin = dpToPx(6);
            if (titleView != null) {
                titleView.setTextColor(0xFFFFFFFF);
            }
            if (summaryView != null) {
                summaryView.setTextColor(0xFFC7D4DF);
            }
            if (iconFrame != null) {
                iconFrame.setVisibility(View.GONE);
            }
        } else {
            if (isFirst && isLast) {
                itemView.setBackgroundResource(R.drawable.bg_battly_settings_item_single);
                layoutParams.bottomMargin = 0;
            } else if (isFirst) {
                itemView.setBackgroundResource(R.drawable.bg_battly_settings_item_top);
                layoutParams.bottomMargin = dpToPx(1);
            } else if (isLast) {
                itemView.setBackgroundResource(R.drawable.bg_battly_settings_item_bottom);
                layoutParams.bottomMargin = 0;
            } else {
                itemView.setBackgroundResource(R.drawable.bg_battly_settings_item_middle);
                layoutParams.bottomMargin = dpToPx(1);
            }

            layoutParams.topMargin = 0;
            if (titleView != null) {
                titleView.setAllCaps(false);
                titleView.setLetterSpacing(0f);
                titleView.setTextColor(BATTLY_PLUS_KEY.equals(preference.getKey()) ? 0xFFFFD95A : 0xFFFFFFFF);
            }
            if (summaryView != null) {
                summaryView.setTextColor(BATTLY_PLUS_KEY.equals(preference.getKey()) ? 0xFFFFEDB0 : 0xFFC7D4DF);
            }
            if (iconFrame != null) {
                if (preference.getIcon() == null) {
                    iconFrame.setVisibility(View.GONE);
                } else {
                    iconFrame.setVisibility(View.VISIBLE);
                    if (iconView != null) {
                        if (BATTLY_PLUS_KEY.equals(preference.getKey())) {
                            iconView.clearColorFilter();
                        } else {
                            iconView.setColorFilter(0xFFFFFFFF);
                        }
                    }
                }
            }
        }
        itemView.setLayoutParams(layoutParams);
    }

    private boolean isSettingsHeader(@NonNull Preference preference) {
        return SETTINGS_HEADER_KEY.equals(preference.getKey());
    }

    private int dpToPx(int dp) {
        return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
    }
}
