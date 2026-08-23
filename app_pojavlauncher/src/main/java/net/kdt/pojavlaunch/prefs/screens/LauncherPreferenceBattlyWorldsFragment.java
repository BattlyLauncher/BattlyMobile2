package net.kdt.pojavlaunch.prefs.screens;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsDiagnostics;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsManager;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsNodeList;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsPreferences;

public class LauncherPreferenceBattlyWorldsFragment extends LauncherPreferenceFragment {
    private ListPreference mDurationPreference;
    private ListPreference mVisibilityPreference;
    private Preference mNotificationStatus;
    private Preference mConnectionStatus;

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        addPreferencesFromResource(R.xml.pref_battlyworlds);
        mDurationPreference = requirePreference(BattlyWorldsPreferences.KEY_DEFAULT_DURATION,
                ListPreference.class);
        mVisibilityPreference = requirePreference(BattlyWorldsPreferences.KEY_DEFAULT_VISIBILITY,
                ListPreference.class);
        mNotificationStatus = requirePreference("battlyworlds_notification_status");
        mConnectionStatus = requirePreference("battlyworlds_connection_status");

        requirePreference("battlyworlds_test_connection").setOnPreferenceClickListener(preference -> {
            testConnection();
            return true;
        });
        requirePreference("battlyworlds_open_diagnostics").setOnPreferenceClickListener(preference -> {
            BattlyWorldsDiagnostics.show(requireActivity());
            return true;
        });
        requirePreference("battlyworlds_voice_overlay_reset").setOnPreferenceClickListener(preference -> {
            BattlyWorldsManager.resetVoiceOverlay(requireContext());
            return true;
        });
        mNotificationStatus.setOnPreferenceClickListener(preference -> {
            openNotificationSettings();
            return true;
        });
        requirePreference(BattlyWorldsPreferences.KEY_ALLOW_PUBLIC_LISTING)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean allowed = Boolean.TRUE.equals(newValue);
                    mVisibilityPreference.setEnabled(allowed);
                    if (!allowed) mVisibilityPreference.setValue("private");
                    return true;
                });

        refreshEntitlementsAndDuration();
        updateVisibilityAvailability();
        updateNotificationStatus();
        updateConnectionStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationStatus();
        updateConnectionStatus();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        super.onSharedPreferenceChanged(preferences, key);
        if (BattlyWorldsPreferences.KEY_ALLOW_PUBLIC_LISTING.equals(key)) updateVisibilityAvailability();
        if (BattlyWorldsPreferences.KEY_INVITATIONS.equals(key) && isAdded()) {
            if (BattlyWorldsPreferences.areInvitationsEnabled(requireContext())) {
                BattlyWorldsInvites.startInvitePolling(requireContext());
            } else {
                BattlyWorldsInvites.stopInvitePolling();
            }
        }
        if (BattlyWorldsPreferences.KEY_VOICE_MUTED.equals(key)
                || BattlyWorldsPreferences.KEY_VOICE_DEAFENED.equals(key)
                || BattlyWorldsPreferences.KEY_VOICE_OVERLAY_ENABLED.equals(key)
                || BattlyWorldsPreferences.KEY_VOICE_OVERLAY_OPACITY.equals(key)) {
            BattlyWorldsManager.applyVoicePreferences(requireContext());
        }
    }

    private void refreshEntitlementsAndDuration() {
        applyDurationOptions(BattlyWorldsInvites.getCachedEntitlements().roomDurationHours);
        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            BattlyWorldsInvites.Entitlements entitlements =
                    BattlyWorldsInvites.refreshEntitlements(appContext);
            Tools.MAIN_HANDLER.post(() -> {
                if (isAdded()) applyDurationOptions(entitlements.roomDurationHours);
            });
        });
    }

    private void applyDurationOptions(int maximumHours) {
        int[] hours = BattlyWorldsPreferences.durationOptions(maximumHours);
        CharSequence[] entries = new CharSequence[hours.length];
        CharSequence[] values = new CharSequence[hours.length];
        for (int i = 0; i < hours.length; i++) {
            entries[i] = getString(R.string.battlyworlds_duration_hours, hours[i]);
            values[i] = Integer.toString(hours[i]);
        }
        mDurationPreference.setEntries(entries);
        mDurationPreference.setEntryValues(values);
        int selected = BattlyWorldsPreferences.getDefaultDurationHours(requireContext(), maximumHours);
        mDurationPreference.setValue(Integer.toString(selected));
        mDurationPreference.setSummary(getString(R.string.battlyworlds_duration_summary,
                selected, maximumHours));
        mDurationPreference.setOnPreferenceChangeListener((preference, value) -> {
            int chosen = Integer.parseInt(String.valueOf(value));
            mDurationPreference.setSummary(getString(R.string.battlyworlds_duration_summary,
                    chosen, maximumHours));
            return true;
        });
    }

    private void updateVisibilityAvailability() {
        mVisibilityPreference.setEnabled(BattlyWorldsPreferences.isPublicListingAllowed(requireContext()));
    }

    private void updateNotificationStatus() {
        boolean permissionGranted = Build.VERSION.SDK_INT < 33
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean enabled = permissionGranted
                && NotificationManagerCompat.from(requireContext()).areNotificationsEnabled();
        mNotificationStatus.setSummary(enabled
                ? R.string.battlyworlds_notifications_enabled
                : R.string.battlyworlds_notifications_blocked);
    }

    private void openNotificationSettings() {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() instanceof LauncherActivity
                && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ((LauncherActivity) getActivity()).askForNotificationPermission(this::updateNotificationStatus);
            return;
        }
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        startActivity(intent);
    }

    private void updateConnectionStatus() {
        String node = BattlyWorldsManager.getLastNode();
        String state = BattlyWorldsManager.describeState(requireContext(), BattlyWorldsManager.getLastState());
        mConnectionStatus.setSummary(getString(R.string.battlyworlds_connection_status_summary,
                node.isEmpty() ? getString(R.string.battlyworlds_node_not_selected) : node,
                state,
                BattlyWorldsManager.getMetadataText(requireContext())));
    }

    private void testConnection() {
        mConnectionStatus.setSummary(R.string.battlyworlds_connection_testing);
        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            BattlyWorldsNodeList.ProbeResult result = BattlyWorldsNodeList.probe(appContext);
            Tools.MAIN_HANDLER.post(() -> {
                if (!isAdded()) return;
                mConnectionStatus.setSummary(result.reachable
                        ? getString(R.string.battlyworlds_connection_test_ok,
                                result.node, result.latencyMs, BattlyWorldsManager.getMetadataText(requireContext()))
                        : getString(R.string.battlyworlds_connection_test_failed,
                                result.node, result.message));
            });
        });
    }
}
