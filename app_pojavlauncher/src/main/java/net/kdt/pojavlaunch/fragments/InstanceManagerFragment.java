package net.kdt.pojavlaunch.fragments;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.gamepad.GamepadMapStore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.ControllerProfileManager;
import net.kdt.pojavlaunch.utils.ContentProfileManager;
import net.kdt.pojavlaunch.utils.CrashAnalysisEngine;
import net.kdt.pojavlaunch.utils.InstanceManager;
import net.kdt.pojavlaunch.utils.ModpackLifecycleManager;
import net.kdt.pojavlaunch.utils.WorldManager;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class InstanceManagerFragment extends Fragment {
    public static final String TAG = "InstanceManagerFragment";

    private LinearLayout mContainer;
    private TextView mStatus;
    private Button mImportButton;
    private int mLoadGeneration;
    private String mPendingExportKey;
    private String mPendingWorldProfileKey;
    private WorldManager.WorldInfo mPendingWorldExport;

    private final ActivityResultLauncher<String> mImportInstance = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::importInstance);
    private final ActivityResultLauncher<String> mExportInstance = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::exportInstance);
    private final ActivityResultLauncher<String> mImportWorld = registerForActivityResult(
            new ActivityResultContracts.GetContent(), this::importWorld);
    private final ActivityResultLauncher<String> mExportWorld = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/zip"), this::exportWorld);

    public InstanceManagerFragment() {
        super(R.layout.fragment_instance_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mContainer = view.findViewById(R.id.instance_manager_container);
        mStatus = view.findViewById(R.id.instance_manager_status);
        mImportButton = view.findViewById(R.id.instance_import_button);
        mImportButton.setOnClickListener(v -> mImportInstance.launch("application/zip"));
        bindInstances();
    }

    private void bindInstances() {
        if (mContainer == null) return;
        if (mImportButton != null) mImportButton.setVisibility(View.VISIBLE);
        if (mStatus != null) mStatus.setVisibility(View.VISIBLE);
        final int generation = ++mLoadGeneration;
        if (mContainer.getChildCount() == 0) mStatus.setText(R.string.instance_manager_loading);
        PojavApplication.sExecutorService.execute(() -> {
            List<InstanceManager.InstanceRecord> records = InstanceManager.list();
            Tools.runOnUiThread(() -> {
                if (!isAdded() || generation != mLoadGeneration) return;
                renderInstances(records, generation);
            });
        });
    }

    private void renderInstances(List<InstanceManager.InstanceRecord> records, int generation) {
        mContainer.removeAllViews();
        mStatus.setText(R.string.instance_manager_current_hint);
        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString(
                LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        Map<String, TextView> pendingSizeLabels = new HashMap<>();
        for (InstanceManager.InstanceRecord record : records) {
            boolean selected = record.key.equals(selectedKey);
            TextView subtitle = addInstanceCard(record, selected, records.size() > 1);
            if (InstanceManager.getCachedDirectorySize(record.gameDirectory()) < 0L) {
                pendingSizeLabels.put(record.key, subtitle);
            }
        }
        if (!pendingSizeLabels.isEmpty()) {
            loadInstanceSizes(records, pendingSizeLabels, selectedKey, generation);
        }
    }

    private TextView addInstanceCard(InstanceManager.InstanceRecord record, boolean selected, boolean canDelete) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(selected ? R.drawable.bg_battly_launcher_play_small : R.drawable.bg_battly_form_panel);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(params);

        TextView title = text(firstNonEmpty(record.profile.name, record.profile.lastVersionId, "Instance"), 16, true);
        card.addView(title);
        long cachedSize = InstanceManager.getCachedDirectorySize(record.gameDirectory());
        String summary = instanceSummary(record, selected, cachedSize);
        TextView subtitle = text(summary, 12, false);
        subtitle.setTextColor(0xFFC7D4DF);
        subtitle.setPadding(0, dp(3), 0, dp(10));
        card.addView(subtitle);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions, R.string.instance_use, () -> {
            InstanceManager.select(record.key);
            GamepadMapStore.invalidate();
            bindInstances();
        });
        addAction(actions, R.string.instance_more, () -> showActions(record));
        if (canDelete) {
            ImageButton delete = new ImageButton(requireContext());
            delete.setImageResource(R.drawable.ic_delete_compact);
            delete.setScaleType(android.widget.ImageView.ScaleType.CENTER);
            delete.setColorFilter(0xFFFF9A9A);
            delete.setBackgroundResource(R.drawable.bg_battly_launcher_chip);
            delete.setContentDescription(getString(R.string.global_delete));
            delete.setPadding(0, 0, 0, 0);
            delete.setOnClickListener(v -> confirmDelete(record));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            deleteParams.setMargins(dp(8), 0, 0, 0);
            actions.addView(delete, deleteParams);
        }
        card.addView(actions);
        mContainer.addView(card);
        return subtitle;
    }

    private void loadInstanceSizes(List<InstanceManager.InstanceRecord> records,
                                   Map<String, TextView> labels, String selectedKey, int generation) {
        PojavApplication.sExecutorService.execute(() -> {
            for (InstanceManager.InstanceRecord record : records) {
                if (!labels.containsKey(record.key)) continue;
                long size = InstanceManager.calculateDirectorySize(record.gameDirectory());
                Tools.runOnUiThread(() -> {
                    if (!isAdded() || generation != mLoadGeneration) return;
                    TextView label = labels.get(record.key);
                    if (label != null) {
                        label.setText(instanceSummary(record, record.key.equals(selectedKey), size));
                    }
                });
            }
        });
    }

    private String instanceSummary(InstanceManager.InstanceRecord record, boolean selected, long sizeBytes) {
        String storage = sizeBytes >= 0L
                ? formatSize(sizeBytes)
                : getString(R.string.instance_storage_calculating);
        return firstNonEmpty(record.profile.lastVersionId, getString(R.string.launcher_version_unknown))
                + "  ·  " + storage
                + (selected ? "  ·  " + getString(R.string.instance_active) : "");
    }

    private void showActions(InstanceManager.InstanceRecord record) {
        renderActionsPanel(record);
    }

    private void renderActionsPanel(InstanceManager.InstanceRecord record) {
        if (mContainer == null) return;
        if (mImportButton != null) mImportButton.setVisibility(View.GONE);
        mLoadGeneration++;
        mContainer.removeAllViews();
        String name = firstNonEmpty(record.profile.name, record.profile.lastVersionId, "Instance");
        mStatus.setVisibility(View.GONE);

        LinearLayout summaryCard = new LinearLayout(requireContext());
        summaryCard.setOrientation(LinearLayout.VERTICAL);
        summaryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        summaryCard.setBackgroundResource(R.drawable.bg_battly_launcher_play);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.setMargins(0, 0, 0, dp(12));
        mContainer.addView(summaryCard, summaryParams);

        TextView title = text(name, 18, true);
        summaryCard.addView(title);
        TextView subtitle = text(instanceSummary(record, false, InstanceManager.getCachedDirectorySize(record.gameDirectory())), 12, false);
        subtitle.setTextColor(0xFFD4E1E8);
        subtitle.setPadding(0, dp(4), 0, 0);
        summaryCard.addView(subtitle);

        LinearLayout topActions = new LinearLayout(requireContext());
        topActions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        topActions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(topActions, R.string.launcher_nav_back, this::bindInstances);
        LinearLayout.LayoutParams topActionParams = new LinearLayout.LayoutParams(-1, -2);
        topActionParams.setMargins(0, 0, 0, dp(6));
        mContainer.addView(topActions, topActionParams);

        String[] actions = getResources().getStringArray(R.array.instance_actions);
        GridLayout actionGrid = new GridLayout(requireContext());
        actionGrid.setAlignmentMode(GridLayout.ALIGN_MARGINS);
        actionGrid.setColumnCount(actionColumnCount());
        actionGrid.setUseDefaultMargins(false);
        mContainer.addView(actionGrid, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < actions.length; i++) {
            if (i == 11) continue;
            addActionCard(actionGrid, record, i, actions[i], actionDescription(i));
        }
    }

    private void addActionCard(GridLayout grid, InstanceManager.InstanceRecord record,
                               int actionIndex, String title, String subtitle) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.START);
        card.setMinimumHeight(dp(128));
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.bg_battly_form_panel);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> executeInstanceAction(record, actionIndex));

        ImageView icon = new ImageView(requireContext());
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(actionIcon(actionIndex));
        card.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView titleView = text(title, 15, true);
        titleView.setPadding(0, dp(12), 0, 0);
        card.addView(titleView);
        if (Tools.isValidString(subtitle)) {
            TextView subtitleView = text(subtitle, 12, false);
            subtitleView.setTextColor(0xFFC7D4DF);
            subtitleView.setPadding(0, dp(4), 0, 0);
            card.addView(subtitleView);
        }

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.setMargins(dp(5), dp(5), dp(5), dp(5));
        grid.addView(card, params);
    }

    private int actionColumnCount() {
        int widthDp = getResources().getConfiguration().screenWidthDp;
        return widthDp >= 720 ? 3 : 2;
    }

    private int actionIcon(int actionIndex) {
        switch (actionIndex) {
            case 0: return R.drawable.minecraft_chest;
            case 1: return R.drawable.minecraft_name_tag;
            case 2: return R.drawable.minecraft_book;
            case 3: return R.drawable.minecraft_nether_star;
            case 4: return R.drawable.minecraft_chiseled_bookshelf;
            case 5: return R.drawable.minecraft_bookshelf;
            case 6: return R.drawable.minecraft_grass_block;
            case 7: return R.drawable.minecraft_tnt;
            case 8: return R.drawable.minecraft_filled_map;
            case 9: return R.drawable.ic_battly_gamepad_line;
            case 10: return R.drawable.ic_folder;
            case 11: return R.drawable.ic_menu_delete_forever;
            default: return R.drawable.minecraft_chest;
        }
    }

    private String actionDescription(int actionIndex) {
        switch (actionIndex) {
            case 0: return getString(R.string.instance_action_duplicate_desc);
            case 1: return getString(R.string.instance_action_rename_desc);
            case 2: return getString(R.string.instance_action_export_desc);
            case 3: return getString(R.string.instance_action_update_desc);
            case 4: return getString(R.string.instance_action_snapshot_desc);
            case 5: return getString(R.string.instance_action_restore_desc);
            case 6: return getString(R.string.instance_action_worlds_desc);
            case 7: return getString(R.string.instance_action_crash_desc);
            case 8: return getString(R.string.instance_action_content_desc);
            case 9: return getString(R.string.instance_action_controller_desc);
            case 10: return getString(R.string.instance_action_open_desc);
            case 11: return getString(R.string.instance_action_delete_desc);
            default: return "";
        }
    }

    private void executeInstanceAction(InstanceManager.InstanceRecord record, int which) {
        switch (which) {
            case 0: promptDuplicate(record); break;
            case 1: promptRename(record); break;
            case 2: mPendingExportKey = record.key; mExportInstance.launch(safeName(record.profile.name) + ".battly.zip"); break;
            case 3: checkModpackUpdate(record); break;
            case 4: createSnapshot(record); break;
            case 5: showSnapshots(record); break;
            case 6: showWorlds(record); break;
            case 7: showCrashDiagnosis(record); break;
            case 8: showContentProfiles(record); break;
            case 9: saveControllerProfile(record); break;
            case 10: Tools.openPath(requireContext(), record.gameDirectory(), false); break;
            case 11: confirmDelete(record); break;
        }
    }

    private void showContentProfiles(InstanceManager.InstanceRecord record) {
        List<File> profiles = ContentProfileManager.list(record.profile);
        String[] labels = new String[profiles.size() + 1];
        labels[0] = getString(R.string.content_profile_save);
        for (int i = 0; i < profiles.size(); i++) labels[i + 1] = profiles.get(i).getName().replaceFirst("\\.json$", "");
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.content_profiles_title)
                .setItems(labels, (d, which) -> {
                    if (which == 0) promptSaveContentProfile(record);
                    else runTask(() -> {
                        InstanceManager.createSnapshot(record.key, "before-content-profile");
                        ContentProfileManager.apply(record.profile, profiles.get(which - 1));
                        return profiles.get(which - 1).getName();
                    }, R.string.content_profile_applied);
                }));
    }

    private void promptSaveContentProfile(InstanceManager.InstanceRecord record) {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.content_profile_name);
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.content_profile_save)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> runTask(() ->
                        ContentProfileManager.save(record.profile, input.getText().toString()).getName(), R.string.content_profile_saved))
                .setNegativeButton(android.R.string.cancel, null));
    }

    private void checkModpackUpdate(InstanceManager.InstanceRecord record) {
        Toast.makeText(requireContext(), R.string.global_wait, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                ModpackLifecycleManager.UpdateInfo update = ModpackLifecycleManager.check(requireContext(), record.profile);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    if (!update.managed) {
                        Toast.makeText(requireContext(), R.string.modpack_not_managed, Toast.LENGTH_LONG).show();
                    } else if (!update.available) {
                        Toast.makeText(requireContext(), R.string.modpack_up_to_date, Toast.LENGTH_LONG).show();
                    } else {
                        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                                .setTitle(R.string.modpack_check_update)
                                .setMessage(getString(R.string.modpack_update_available, update.versionName))
                                .setPositiveButton(R.string.modpack_update_install, (d, w) -> runTask(() ->
                                        ModpackLifecycleManager.update(requireContext(), record.key, update), R.string.modpack_updated))
                                .setNegativeButton(android.R.string.cancel, null));
                    }
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> { if (isAdded()) Tools.showError(requireContext(), throwable); });
            }
        });
    }

    private void promptDuplicate(InstanceManager.InstanceRecord record) {
        EditText input = new EditText(requireContext());
        String sourceName = firstNonEmpty(record.profile.name, getString(R.string.instance_default_name));
        input.setText(getString(R.string.instance_copy_name, sourceName));
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.instance_duplicate)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> runTask(() ->
                        InstanceManager.duplicate(record.key, input.getText().toString()), R.string.instance_duplicated))
                .setNegativeButton(android.R.string.cancel, null));
    }

    private void promptRename(InstanceManager.InstanceRecord record) {
        EditText input = new EditText(requireContext());
        input.setText(record.profile.name);
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.instance_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    InstanceManager.rename(record.key, input.getText().toString());
                    bindInstances();
                })
                .setNegativeButton(android.R.string.cancel, null));
    }

    private void createSnapshot(InstanceManager.InstanceRecord record) {
        runTask(() -> InstanceManager.createSnapshot(record.key, "manual").getAbsolutePath(), R.string.instance_snapshot_created);
    }

    private void showSnapshots(InstanceManager.InstanceRecord record) {
        List<File> snapshots = InstanceManager.snapshots(record.key);
        if (snapshots.isEmpty()) {
            Toast.makeText(requireContext(), R.string.instance_no_snapshots, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[snapshots.size()];
        for (int i = 0; i < snapshots.size(); i++) names[i] = DateFormat.getDateTimeInstance().format(new Date(snapshots.get(i).lastModified()));
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.instance_restore_snapshot)
                .setItems(names, (d, which) -> runTask(() -> {
                    InstanceManager.rollback(record.key, snapshots.get(which));
                    return record.key;
                }, R.string.instance_snapshot_restored)));
    }

    private void showWorlds(InstanceManager.InstanceRecord record) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AlertDialog loading = Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.world_manager_title)
                .setMessage(R.string.world_manager_loading));
        loading.setCanceledOnTouchOutside(false);
        loading.setOnCancelListener(dialog -> cancelled.set(true));
        PojavApplication.sExecutorService.execute(() -> {
            List<WorldManager.WorldInfo> worlds = WorldManager.list(record.profile);
            Tools.runOnUiThread(() -> {
                if (loading.isShowing()) loading.dismiss();
                if (!isAdded() || cancelled.get()) return;
                showWorldList(record, worlds);
            });
        });
    }

    private void showWorldList(InstanceManager.InstanceRecord record, List<WorldManager.WorldInfo> worlds) {
        String[] labels = new String[worlds.size() + 1];
        labels[0] = getString(R.string.world_import);
        for (int i = 0; i < worlds.size(); i++) {
            WorldManager.WorldInfo world = worlds.get(i);
            labels[i + 1] = world.displayName + "  ·  " + formatSize(world.sizeBytes);
        }
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.world_manager_title)
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        mPendingWorldProfileKey = record.key;
                        mImportWorld.launch("application/zip");
                    } else showWorldActions(record, worlds.get(which - 1));
                }));
    }

    private void showWorldActions(InstanceManager.InstanceRecord record, WorldManager.WorldInfo world) {
        String[] labels = {getString(R.string.world_export), getString(R.string.world_duplicate),
                getString(R.string.global_delete), getString(R.string.global_open)};
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(world.displayName)
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        mPendingWorldExport = world;
                        mExportWorld.launch(safeName(world.displayName) + ".zip");
                    } else if (which == 1) {
                        runTask(() -> WorldManager.duplicate(world, record.profile, world.displayName + " copy").getName(), R.string.world_duplicated);
                    } else if (which == 2) {
                        runTask(() -> { WorldManager.delete(world); return world.displayName; }, R.string.world_deleted);
                    } else {
                        Tools.openPath(requireContext(), world.directory, false);
                    }
                }));
    }

    private void showCrashDiagnosis(InstanceManager.InstanceRecord record) {
        CrashAnalysisEngine.Report report = CrashAnalysisEngine.analyze(record.profile, -1, null);
        if (report.findings.isEmpty()) {
            Toast.makeText(requireContext(), R.string.crash_no_findings, Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[report.findings.size()];
        for (int i = 0; i < labels.length; i++) {
            CrashAnalysisEngine.Finding finding = report.findings.get(i);
            labels[i] = finding.title + "\n" + finding.recommendation;
        }
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.crash_diagnosis_title)
                .setItems(labels, (d, which) -> {
                    CrashAnalysisEngine.Finding finding = report.findings.get(which);
                    runTask(() -> {
                        boolean changed = CrashAnalysisEngine.applyRecovery(record.profile, finding);
                        if (changed) LauncherProfiles.write();
                        return String.valueOf(changed);
                    }, R.string.crash_recovery_applied);
                }));
    }

    private void saveControllerProfile(InstanceManager.InstanceRecord record) {
        runTask(() -> {
            ControllerProfileManager.save(record.profile);
            return record.key;
        }, R.string.controller_profile_saved);
    }

    private void confirmDelete(InstanceManager.InstanceRecord record) {
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.global_delete)
                .setMessage(R.string.instance_delete_confirm)
                .setPositiveButton(R.string.global_delete, (d, w) -> runTask(() -> {
                    InstanceManager.delete(record.key, true);
                    return record.key;
                }, R.string.instance_deleted))
                .setNegativeButton(android.R.string.cancel, null));
    }

    private void importInstance(Uri uri) {
        if (uri == null) return;
        runTask(() -> {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Unable to open archive");
                return InstanceManager.importInstance(input, "Imported instance");
            }
        }, R.string.instance_imported);
    }

    private void exportInstance(Uri uri) {
        String key = mPendingExportKey;
        mPendingExportKey = null;
        if (uri == null || key == null) return;
        runTask(() -> {
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Unable to create archive");
                InstanceManager.exportInstance(key, output);
                return key;
            }
        }, R.string.instance_exported);
    }

    private void importWorld(Uri uri) {
        String key = mPendingWorldProfileKey;
        mPendingWorldProfileKey = null;
        if (uri == null || key == null) return;
        MinecraftProfile profile = profile(key);
        runTask(() -> {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Unable to open world");
                return WorldManager.importWorld(profile, input, "Imported world").getName();
            }
        }, R.string.world_imported);
    }

    private void exportWorld(Uri uri) {
        WorldManager.WorldInfo world = mPendingWorldExport;
        mPendingWorldExport = null;
        if (uri == null || world == null) return;
        runTask(() -> {
            try (OutputStream output = requireContext().getContentResolver().openOutputStream(uri)) {
                if (output == null) throw new IllegalStateException("Unable to create world archive");
                WorldManager.exportWorld(world, output);
                return world.displayName;
            }
        }, R.string.world_exported);
    }

    private MinecraftProfile profile(String key) {
        LauncherProfiles.load();
        return LauncherProfiles.mainProfileJson.profiles.get(key);
    }

    private void runTask(Task task, int successMessage) {
        Toast.makeText(requireContext(), R.string.global_wait, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                task.run();
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
                    bindInstances();
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> { if (isAdded()) Tools.showError(requireContext(), throwable); });
            }
        });
    }

    private void addAction(LinearLayout parent, int text, Runnable action) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextColor(0xFFFFFFFF);
        button.setTextSize(12);
        button.setBackgroundResource(R.drawable.bg_battly_button_secondary);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(42));
        params.setMargins(dp(8), 0, 0, 0);
        parent.addView(button, params);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView text = new TextView(requireContext());
        text.setText(value);
        text.setTextColor(0xFFFFFFFF);
        text.setTextSize(size);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824d);
        if (bytes >= 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576d);
        return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024d);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (Tools.isValidString(value)) return value;
        return "";
    }

    private static String safeName(String value) {
        String safe = value == null ? "instance" : value.replaceAll("[^A-Za-z0-9._-]+", "-");
        return safe.isEmpty() ? "instance" : safe;
    }

    private interface Task { Object run() throws Exception; }
}
