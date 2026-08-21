package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlyPlusCloud;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.List;

public class BattlyPlusFragment extends Fragment {
    public static final String TAG = "BattlyPlusFragment";

    private static final int COLOR_PANEL = 0xD9142232;
    private static final int COLOR_PANEL_SOFT = 0xAE1B3140;
    private static final int COLOR_ACCENT = 0xFF8DEEDC;
    private static final int COLOR_GOLD = 0xFFFFD95A;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFFC7D4DF;

    private LinearLayout mRoot;
    private ActivityResultLauncher<Intent> mDriveSignInLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDriveSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != Activity.RESULT_OK) {
                        showActionResult(false, getString(R.string.battly_plus_drive_login_cancelled));
                        return;
                    }
                    try {
                        GoogleSignInAccount account = GoogleSignIn
                                .getSignedInAccountFromIntent(result.getData())
                                .getResult(ApiException.class);
                        uploadDriveBackup(account);
                    } catch (ApiException e) {
                        showActionResult(false, getString(R.string.battly_plus_drive_account_required));
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        scrollView.setClipToPadding(false);

        mRoot = new LinearLayout(requireContext());
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setPadding(dp(24), dp(16), dp(24), dp(30));
        scrollView.addView(mRoot, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        bindContent(BattlyPlusManager.isPlus(requireContext()));
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        BattlyPlusManager.refreshAsync(requireContext(), plus -> {
            if (isAdded()) {
                bindContent(plus);
            }
        });
    }

    private void bindContent(boolean plus) {
        if (mRoot == null) {
            return;
        }
        mRoot.removeAllViews();
        addHero(plus);
        addQuickActions(plus);
        addSectionTitle(R.string.battly_plus_perks_title, R.string.battly_plus_perks_subtitle);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(12), 0, 0);
        mRoot.addView(row, rowParams);

        addPerkCard(row, R.drawable.minecraft_nether_star,
                R.string.battly_plus_feature_backgrounds_title,
                R.string.battly_plus_feature_backgrounds_desc,
                plus, v -> openBackgroundSettings(), true);
        addPerkCard(row, R.drawable.logo,
                R.string.battly_plus_feature_worlds_title,
                R.string.battly_plus_feature_worlds_desc,
                plus, null, false);

        LinearLayout row2 = addPerkRow();
        addPerkCard(row2, R.drawable.ic_bookshelf,
                R.string.battly_plus_feature_cloud_sync_title,
                R.string.battly_plus_feature_cloud_sync_desc,
                plus, v -> runCloudSync(), true);
        addPerkCard(row2, R.drawable.minecraft_filled_map,
                R.string.battly_plus_feature_backups_title,
                R.string.battly_plus_feature_backups_desc,
                plus, v -> runBackups(), false);

        LinearLayout row3 = addPerkRow();
        addPerkCard(row3, R.drawable.minecraft_chest,
                R.string.battly_plus_feature_shared_installs_title,
                R.string.battly_plus_feature_shared_installs_desc,
                plus, v -> shareInstallation(), true);
        addPerkCard(row3, R.drawable.minecraft_nether_star,
                R.string.battly_plus_feature_mod_updates_title,
                R.string.battly_plus_feature_mod_updates_desc,
                plus, v -> checkModUpdates(), false);

        LinearLayout row4 = addPerkRow();
        addPerkCard(row4, R.drawable.ic_battly_pickaxe,
                R.string.battly_plus_feature_boost_title,
                R.string.battly_plus_feature_boost_desc,
                plus, v -> applyBoost(), true);
        addQueueCard(row4, plus);

        LinearLayout row5 = addPerkRow();
        addWorldsPlusCard(row5, plus);

        LinearLayout row6 = addPerkRow();
        addPerkCard(row6, R.drawable.logo,
                R.string.battly_plus_feature_app_icons_title,
                R.string.battly_plus_feature_app_icons_desc,
                plus, v -> openIconSettings(), true);
    }

    private void addHero(boolean plus) {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setBackground(makeGradient(28, 0xE01A2D3A, 0xCC101B2A, COLOR_ACCENT, 0.16f));
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        mRoot.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        hero.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(requireContext());
        logo.setImageResource(R.drawable.logo);
        logo.setPadding(dp(13), dp(13), dp(13), dp(13));
        logo.setBackground(makeRound(24, 0x3312E6CC, 0x268DEEDC));
        top.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));

        LinearLayout titleBlock = new LinearLayout(requireContext());
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams titleBlockParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBlockParams.setMargins(dp(18), 0, dp(12), 0);
        top.addView(titleBlock, titleBlockParams);

        TextView eyebrow = text(plus ? R.string.battly_plus_status_active : R.string.battly_plus_locked_short,
                plus ? COLOR_ACCENT : COLOR_GOLD, 12, true);
        eyebrow.setBackground(makeRound(999, plus ? 0x2237E9C5 : 0x22FFD95A,
                plus ? 0x558DEEDC : 0x66FFD95A));
        eyebrow.setGravity(Gravity.CENTER);
        eyebrow.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams eyebrowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleBlock.addView(eyebrow, eyebrowParams);

        TextView title = text(R.string.battly_plus_title, COLOR_TEXT, 30, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(6), 0, 0);
        titleBlock.addView(title, titleParams);

        TextView desc = text(R.string.battly_plus_subtitle, COLOR_MUTED, 15, false);
        desc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(3), 0, 0);
        titleBlock.addView(desc, descParams);

        TextView cta = text(plus ? R.string.battly_plus_cta_backgrounds : R.string.battly_plus_cta_locked,
                plus ? 0xFF0C2430 : COLOR_GOLD, 13, true);
        cta.setGravity(Gravity.CENTER);
        cta.setPadding(dp(14), dp(10), dp(14), dp(10));
        cta.setBackground(makeRound(999, plus ? COLOR_ACCENT : 0x22FFD95A, plus ? 0 : 0x66FFD95A));
        cta.setOnClickListener(v -> {
            if (plus) {
                openBackgroundSettings();
            }
        });
        top.addView(cta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.setMargins(0, dp(18), 0, 0);
        content.addView(chips, chipsParams);

        addHeroChip(chips, R.string.battly_plus_chip_cloud);
        addHeroChip(chips, R.string.battly_plus_chip_worlds);
        addHeroChip(chips, R.string.battly_plus_chip_queue);
    }

    private void addQuickActions(boolean plus) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(14), 0, 0);
        mRoot.addView(row, rowParams);

        addAction(row, R.string.battly_plus_action_cloud_restore, R.string.battly_plus_action_cloud_restore_desc,
                R.drawable.background, plus, v -> restoreCloudSync());
        addAction(row, R.string.battly_plus_action_cloud_sync, R.string.battly_plus_action_cloud_sync_desc,
                R.drawable.ic_bookshelf, plus, v -> runCloudSync());
        addAction(row, R.string.battly_plus_action_boost, R.string.battly_plus_action_boost_desc,
                R.drawable.ic_battly_pickaxe, plus, v -> applyBoost());
    }

    private LinearLayout addPerkRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(12), 0, 0);
        mRoot.addView(row, params);
        return row;
    }

    private void addSectionTitle(int titleRes, int descRes) {
        TextView title = text(titleRes, COLOR_TEXT, 19, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(20), 0, 0);
        mRoot.addView(title, titleParams);

        TextView desc = text(descRes, COLOR_MUTED, 13, false);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(4), 0, 0);
        mRoot.addView(desc, descParams);
    }

    private void addHeroChip(LinearLayout parent, int textRes) {
        TextView chip = text(textRes, COLOR_MUTED, 12, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(12), dp(8), dp(12), dp(8));
        chip.setBackground(makeRound(999, 0x251B3B49, 0x338DEEDC));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(10), 0);
        parent.addView(chip, params);
    }

    private void addAction(LinearLayout parent, int titleRes, int descRes, int iconRes,
                           boolean available, @Nullable View.OnClickListener clickListener) {
        LinearLayout card = compactCard(available ? COLOR_PANEL_SOFT : 0x80142232, available ? 0x338DEEDC : 0x22FFD95A);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOnClickListener(clickListener);
        card.setClickable(clickListener != null);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(96), 1f);
        params.setMargins(0, 0, dp(12), 0);
        parent.addView(card, params);

        ImageView icon = icon(iconRes, 46, 10, available ? 0x3337E9C5 : 0x20FFD95A);
        card.addView(icon);

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(12), 0, 0, 0);
        card.addView(texts, textParams);

        texts.addView(text(titleRes, COLOR_TEXT, 15, true));
        TextView desc = text(descRes, COLOR_MUTED, 12, false);
        desc.setMaxLines(2);
        texts.addView(desc);
    }

    private void addPerkCard(LinearLayout parent, int iconRes, int titleRes, int descRes, boolean plus,
                             @Nullable View.OnClickListener clickListener, boolean primary) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(makeGradient(24, primary ? 0xCC1A3841 : COLOR_PANEL,
                0xB5111F2E, primary ? COLOR_ACCENT : 0x33B9D6E8, primary ? 0.28f : 0.12f));
        card.setClickable(clickListener != null);
        card.setFocusable(clickListener != null);
        if (clickListener != null) {
            card.setOnClickListener(clickListener);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(172), 1f);
        params.setMargins(0, 0, dp(12), 0);
        parent.addView(card, params);

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        top.addView(icon(iconRes, 48, 10, primary ? 0x3337E9C5 : 0x251B3B49));

        TextView badge = text(plus ? R.string.battly_plus_unlocked_badge : R.string.battly_plus_locked_badge,
                plus ? COLOR_ACCENT : COLOR_GOLD, 11, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(5), dp(10), dp(5));
        badge.setBackground(makeRound(999, plus ? 0x2237E9C5 : 0x22FFD95A,
                plus ? 0x558DEEDC : 0x55FFD95A));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.setMargins(dp(10), 0, 0, 0);
        top.addView(badge, badgeParams);

        TextView title = text(titleRes, COLOR_TEXT, 17, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(14), 0, 0);
        card.addView(title, titleParams);

        TextView desc = text(descRes, COLOR_MUTED, 12, false);
        desc.setLineSpacing(dp(2), 1f);
        desc.setMaxLines(3);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(4), 0, 0);
        card.addView(desc, descParams);
    }

    private void addWorldsPlusCard(LinearLayout parent, boolean plus) {
        LinearLayout card = compactCard(0xB5162C37, plus ? 0x338DEEDC : 0x22FFD95A);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(172), 1f);
        parent.addView(card, params);

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(icon(R.drawable.ic_battly_worlds_line, 48, 11, 0x2537E9C5));

        TextView title = text(R.string.battly_plus_worlds_limits_title, COLOR_TEXT, 17, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);

        LinearLayout chips = new LinearLayout(requireContext());
        chips.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams chipsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        chipsParams.setMargins(0, dp(14), 0, 0);
        card.addView(chips, chipsParams);

        chips.addView(text(R.string.battly_plus_worlds_priority, plus ? COLOR_ACCENT : COLOR_MUTED, 13, true));
        chips.addView(text(R.string.battly_plus_worlds_persistent, plus ? COLOR_ACCENT : COLOR_MUTED, 13, true));
        chips.addView(text(R.string.battly_plus_worlds_invites, plus ? COLOR_ACCENT : COLOR_MUTED, 13, true));
    }

    private void addQueueCard(LinearLayout parent, boolean plus) {
        LinearLayout card = compactCard(0xB5162C37, plus ? 0x338DEEDC : 0x22FFD95A);
        card.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(172), 1f);
        parent.addView(card, params);

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        top.addView(icon(R.drawable.minecraft_chiseled_bookshelf, 48, 11, 0x2537E9C5));

        TextView title = text(R.string.battly_plus_feature_queue_title, COLOR_TEXT, 17, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMargins(dp(12), 0, 0, 0);
        top.addView(title, titleParams);

        TextView desc = text(plus ? R.string.battly_plus_feature_queue_desc_plus
                : R.string.battly_plus_feature_queue_desc_free, COLOR_MUTED, 12, false);
        desc.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(14), 0, 0);
        card.addView(desc, descParams);
    }

    private LinearLayout compactCard(int fill, int stroke) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(makeRound(24, fill, stroke));
        return card;
    }

    private ImageView icon(int res, int sizeDp, int paddingDp, int fill) {
        ImageView image = new ImageView(requireContext());
        image.setImageResource(res);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        image.setBackground(makeRound(18, fill, 0x228DEEDC));
        image.setColorFilter(null);
        image.setAdjustViewBounds(true);
        image.setMinimumWidth(dp(sizeDp));
        image.setMinimumHeight(dp(sizeDp));
        image.setMaxWidth(dp(sizeDp));
        image.setMaxHeight(dp(sizeDp));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return image;
    }

    private TextView text(int stringRes, int color, int sp, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(stringRes);
        textView.setTextColor(color);
        textView.setTextSize(sp);
        textView.setIncludeFontPadding(true);
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return textView;
    }

    private GradientDrawable makeRound(int radiusDp, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(1), stroke);
        }
        return drawable;
    }

    private GradientDrawable makeGradient(int radiusDp, int startColor, int endColor, int strokeColor,
                                          float strokeAlpha) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{startColor, endColor});
        drawable.setCornerRadius(dp(radiusDp));
        int alpha = Math.min(255, Math.max(0, Math.round(255 * strokeAlpha)));
        drawable.setStroke(dp(1), (alpha << 24) | (strokeColor & 0x00FFFFFF));
        return drawable;
    }

    private void openBackgroundSettings() {
        Tools.swapFragment(requireActivity(), BackgroundSettingsFragment.class,
                BackgroundSettingsFragment.TAG, null);
    }

    private void openIconSettings() {
        if (!BattlyPlusManager.isPlus(requireContext())) {
            showActionResult(false, getString(R.string.battly_plus_required));
            return;
        }
        Tools.swapFragment(requireActivity(), BattlyIconSettingsFragment.class,
                BattlyIconSettingsFragment.TAG, null);
    }

    private void runCloudSync() {
        BattlyPlusCloud.syncNow(requireContext(), this::showActionResult);
    }

    private void restoreCloudSync() {
        BattlyPlusCloud.restoreLatestSync(requireContext(), this::showActionResult);
    }

    private void runBackups() {
        if (!BattlyPlusManager.isPlus(requireContext())) {
            showActionResult(false, getString(R.string.battly_plus_required));
            return;
        }
        Scope driveScope = new Scope(BattlyPlusCloud.DRIVE_FILE_SCOPE);
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
        if (account != null && GoogleSignIn.hasPermissions(account, driveScope)) {
            uploadDriveBackup(account);
            return;
        }
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(driveScope)
                .build();
        mDriveSignInLauncher.launch(GoogleSignIn.getClient(requireContext(), options).getSignInIntent());
    }

    private void uploadDriveBackup(GoogleSignInAccount account) {
        showActionResult(true, getString(R.string.battly_plus_drive_backup_started));
        BattlyPlusCloud.uploadAllWorldsToGoogleDrive(requireContext(), account, this::showActionResult);
    }

    private void shareInstallation() {
        Log.i(TAG, "shareInstallation clicked");
        selectProfile(R.string.battly_plus_select_install_profile_title, profile ->
                BattlyPlusCloud.shareCurrentInstallation(requireContext(), profile, (ok, message) -> {
                    showActionResult(ok, message);
                    if (ok && isAdded()) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("text/plain");
                        intent.putExtra(Intent.EXTRA_TEXT, message);
                        startActivity(Intent.createChooser(intent, getString(R.string.battly_plus_share_installation)));
                    }
                }));
    }

    private void checkModUpdates() {
        Log.i(TAG, "checkModUpdates clicked");
        selectProfile(R.string.battly_plus_select_mod_profile_title,
                profile -> BattlyPlusCloud.checkModUpdates(requireContext(), profile, this::showActionResult));
    }

    private void applyBoost() {
        BattlyPlusCloud.applyBattlyBoost(requireContext(), this::showActionResult);
    }

    private void showActionResult(boolean ok, String message) {
        if (!isAdded()) {
            return;
        }
        Log.i(TAG, "action result ok=" + ok + " message=" + message);
        Toast.makeText(requireContext(), message, ok ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    private void selectProfile(int titleRes, ProfileConsumer consumer) {
        List<MinecraftProfile> profiles = BattlyPlusCloud.getAvailableProfiles();
        if (profiles.size() <= 1) {
            consumer.accept(profiles.isEmpty() ? null : profiles.get(0));
            return;
        }
        String[] labels = new String[profiles.size()];
        for (int i = 0; i < profiles.size(); i++) {
            MinecraftProfile profile = profiles.get(i);
            labels[i] = getString(R.string.battly_plus_profile_option,
                    profile.name == null || profile.name.trim().isEmpty()
                            ? getString(R.string.launcher_version_unknown) : profile.name,
                    profile.lastVersionId == null || profile.lastVersionId.trim().isEmpty()
                            ? getString(R.string.launcher_version_unknown) : profile.lastVersionId);
        }
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(titleRes)
                .setItems(labels, (d, which) -> consumer.accept(profiles.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.show();
        Tools.styleDialog(dialog);
    }

    private interface ProfileConsumer {
        void accept(@Nullable MinecraftProfile profile);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
