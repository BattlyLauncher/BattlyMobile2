package net.kdt.pojavlaunch;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.drawerlayout.widget.DrawerLayout;

import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.EditorExitable;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

public class CustomControlsActivity extends BaseActivity implements EditorExitable {
    public static final String EXTRA_PREVIEW_CONTROL_PATH = "previewControlPath";
    public static final String EXTRA_PREVIEW_CONTROL_NAME = "previewControlName";

    private DrawerLayout mDrawerLayout;
    private View mDrawerPanel;
    private View mPullDrawerButton;
    private LinearLayout mInstalledControlsContainer;
    private LinearLayout mActionsContainer;
    private TextView mEmptyInstalledControls;
    private TextView mHintView;
    private TextView mPreviewBadge;
    private ControlLayout mControlLayout;
    private boolean mPreviewMode;
    private String mPreviewPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_custom_controls);

        mControlLayout = findViewById(R.id.customctrl_controllayout);
        mDrawerLayout = findViewById(R.id.customctrl_drawerlayout);
        mDrawerPanel = findViewById(R.id.customctrl_drawer_panel);
        mPullDrawerButton = findViewById(R.id.drawer_button);
        mInstalledControlsContainer = findViewById(R.id.customctrl_installed_controls_container);
        mActionsContainer = findViewById(R.id.customctrl_actions_container);
        mEmptyInstalledControls = findViewById(R.id.customctrl_empty_installed);
        mHintView = findViewById(R.id.customctrl_hint);
        mPreviewBadge = findViewById(R.id.customctrl_preview_badge);

        mPullDrawerButton.setOnClickListener(v -> mDrawerLayout.openDrawer(mDrawerPanel));
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        mPreviewPath = getIntent().getStringExtra(EXTRA_PREVIEW_CONTROL_PATH);
        mPreviewMode = mPreviewPath != null && !mPreviewPath.isEmpty();
        if (mPreviewMode) {
            configurePreviewMode();
        } else {
            populateActionItems();
            refreshInstalledControls();
        }

        mControlLayout.setModifiable(true);
        try {
            mControlLayout.loadLayout(mPreviewMode ? mPreviewPath : LauncherPreferences.PREF_DEFAULTCTRL_PATH);
        } catch (IOException e) {
            Tools.showError(this, e);
        }
    }

    private void configurePreviewMode() {
        String previewName = getIntent().getStringExtra(EXTRA_PREVIEW_CONTROL_NAME);
        mPullDrawerButton.setVisibility(View.GONE);
        mDrawerPanel.setVisibility(View.GONE);
        mHintView.setText(R.string.customctrl_preview_hint);
        mPreviewBadge.setText(getString(
                R.string.customctrl_preview_badge,
                previewName == null || previewName.isEmpty()
                        ? getString(R.string.customctrl_preview_fallback_name)
                        : previewName));
        mPreviewBadge.setVisibility(View.VISIBLE);
    }

    private void populateActionItems() {
        String[] labels = getResources().getStringArray(R.array.menu_customcontrol_customactivity);
        mActionsContainer.removeAllViews();
        for (int position = 0; position < labels.length; position++) {
            final int action = position;
            TextView item = createDrawerItem(labels[position], false);
            item.setOnClickListener(v -> {
                handleAction(action);
                mDrawerLayout.closeDrawers();
            });
            mActionsContainer.addView(item);
        }
    }

    private void handleAction(int position) {
        switch (position) {
            case 0:
                mControlLayout.addControlButton(new ControlData("New"));
                break;
            case 1:
                mControlLayout.addDrawer(new ControlDrawerData());
                break;
            case 2:
                mControlLayout.addJoystickButton(new ControlJoystickData());
                break;
            case 3:
                mControlLayout.openLoadDialog();
                break;
            case 4:
                mControlLayout.openSaveDialog(this);
                break;
            case 5:
                mControlLayout.openSetDefaultDialog();
                break;
            case 6:
                exportCurrentControl();
                break;
            default:
                break;
        }
    }

    private void exportCurrentControl() {
        try {
            Uri contentUri = DocumentsContract.buildDocumentUri(
                    getString(R.string.storageProviderAuthorities),
                    mControlLayout.saveToDirectory(mControlLayout.mLayoutFileName));

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.setType("application/json");

            Intent sendIntent = Intent.createChooser(shareIntent, mControlLayout.mLayoutFileName);
            startActivity(sendIntent);
        } catch (Exception e) {
            Tools.showError(this, e);
        }
    }

    private void refreshInstalledControls() {
        mInstalledControlsContainer.removeAllViews();
        File controlsDir = new File(Tools.CTRLMAP_PATH);
        File[] files = controlsDir.exists()
                ? controlsDir.listFiles(file -> file.isFile() && file.getName().endsWith(".json"))
                : null;
        if (files == null || files.length == 0) {
            mEmptyInstalledControls.setVisibility(View.VISIBLE);
            return;
        }

        Arrays.sort(files, Comparator.comparing(file -> file.getName().toLowerCase()));
        mEmptyInstalledControls.setVisibility(View.GONE);
        for (File file : files) {
            boolean isSelected = file.getAbsolutePath().equals(LauncherPreferences.PREF_DEFAULTCTRL_PATH);
            TextView item = createDrawerItem(stripJsonExtension(file.getName()), isSelected);
            item.setOnClickListener(v -> {
                try {
                    mControlLayout.loadLayout(file.getAbsolutePath());
                    LauncherPreferences.PREF_DEFAULTCTRL_PATH = file.getAbsolutePath();
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString("defaultCtrl", file.getAbsolutePath())
                            .apply();
                    refreshInstalledControls();
                } catch (IOException e) {
                    Tools.showError(this, e);
                }
                mDrawerLayout.closeDrawers();
            });
            mInstalledControlsContainer.addView(item);
        }
    }

    private TextView createDrawerItem(String label, boolean selected) {
        TextView item = new TextView(this);
        item.setText(label);
        item.setTextColor(getColor(android.R.color.white));
        item.setTextSize(14);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        item.setBackgroundResource(selected
                ? R.drawable.bg_custom_controls_item_selected
                : R.drawable.bg_custom_controls_item);
        item.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44));
        params.setMargins(0, 0, 0, dp(8));
        item.setLayoutParams(params);
        return item;
    }

    private static String stripJsonExtension(String fileName) {
        return fileName.endsWith(".json")
                ? fileName.substring(0, fileName.length() - 5)
                : fileName;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mPreviewMode && mInstalledControlsContainer != null) {
            refreshInstalledControls();
        }
    }

    @Override
    public void onBackPressed() {
        if (mPreviewMode) {
            super.onBackPressed();
        } else {
            mControlLayout.askToExit(this);
        }
    }

    @Override
    public void exitEditor() {
        super.onBackPressed();
    }
}
