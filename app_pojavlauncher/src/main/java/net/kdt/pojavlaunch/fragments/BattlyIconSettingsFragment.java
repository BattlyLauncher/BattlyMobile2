package net.kdt.pojavlaunch.fragments;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
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
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.BattlyAppIconManager;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

public class BattlyIconSettingsFragment extends Fragment {
    public static final String TAG = "BattlyIconSettingsFragment";

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFFC7D4DF;
    private static final int COLOR_ACCENT = 0xFF8DEEDC;
    private static final int COLOR_GOLD = 0xFFFFD95A;

    private LinearLayout mGrid;
    private String mSelectedIconId;
    private boolean mIsPlus;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        mIsPlus = BattlyPlusManager.isPlus(requireContext());
        mSelectedIconId = BattlyAppIconManager.getSelectedIconId(requireContext());

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(30));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addHero(root);
        mGrid = new LinearLayout(requireContext());
        mGrid.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        gridParams.setMargins(0, dp(16), 0, 0);
        root.addView(mGrid, gridParams);
        bindIcons();
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        BattlyPlusManager.refreshAsync(requireContext(), plus -> {
            if (!isAdded()) {
                return;
            }
            mIsPlus = plus;
            bindIcons();
        });
    }

    private void addHero(LinearLayout parent) {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setBackground(makeGradient(28, 0xE01A2D3A, 0xCC101B2A, COLOR_ACCENT, 0.16f));
        hero.setPadding(dp(22), dp(20), dp(22), dp(20));
        parent.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout content = new LinearLayout(requireContext());
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(requireContext());
        logo.setImageResource(R.drawable.logo);
        logo.setPadding(dp(14), dp(14), dp(14), dp(14));
        logo.setBackground(makeRound(24, 0x3337E9C5, 0x558DEEDC));
        content.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(18), 0, dp(12), 0);
        content.addView(texts, textParams);

        TextView badge = text(mIsPlus ? R.string.battly_plus_status_active : R.string.battly_plus_locked_short,
                mIsPlus ? COLOR_ACCENT : COLOR_GOLD, 12, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(10), dp(4), dp(10), dp(4));
        badge.setBackground(makeRound(999, mIsPlus ? 0x2237E9C5 : 0x22FFD95A,
                mIsPlus ? 0x558DEEDC : 0x66FFD95A));
        texts.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(R.string.battly_plus_app_icon_title, COLOR_TEXT, 28, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(6), 0, 0);
        texts.addView(title, titleParams);

        TextView desc = text(R.string.battly_plus_app_icon_desc, COLOR_MUTED, 14, false);
        desc.setLineSpacing(dp(2), 1f);
        texts.addView(desc);
    }

    private void bindIcons() {
        mGrid.removeAllViews();
        BattlyAppIconManager.IconOption[] options = BattlyAppIconManager.getOptions();
        for (int i = 0; i < options.length; i++) {
            BattlyAppIconManager.IconOption option = options[i];
            addIconCard(option, i + 1 < options.length);
        }
    }

    private void addIconCard(BattlyAppIconManager.IconOption option, boolean addMargin) {
        boolean selected = option.id.equals(mSelectedIconId);
        LinearLayout card = new LinearLayout(requireContext());
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(14));
        card.setBackground(makeGradient(24,
                selected ? 0xCC1A3841 : 0xD9142232,
                0xB5111F2E,
                selected ? COLOR_ACCENT : 0x33B9D6E8,
                selected ? 0.48f : 0.12f));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (!mIsPlus && !BattlyAppIconManager.ICON_DEFAULT.equals(option.id)) {
                Toast.makeText(requireContext(), R.string.battly_plus_required, Toast.LENGTH_SHORT).show();
                return;
            }
            BattlyAppIconManager.applyIcon(requireContext(), option.id);
            mSelectedIconId = option.id;
            bindIcons();
            Toast.makeText(requireContext(), R.string.battly_plus_app_icon_applied, Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(170), 1f);
        params.setMargins(0, 0, addMargin ? dp(12) : 0, 0);
        mGrid.addView(card, params);

        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(option.iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(icon, new LinearLayout.LayoutParams(dp(76), dp(76)));

        TextView title = text(option.titleRes, COLOR_TEXT, 16, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(12), 0, 0);
        card.addView(title, titleParams);

        TextView state = text(selected ? R.string.battly_plus_app_icon_selected
                : mIsPlus || BattlyAppIconManager.ICON_DEFAULT.equals(option.id)
                ? R.string.battly_plus_app_icon_choose
                : R.string.battly_plus_locked_short,
                selected ? COLOR_ACCENT : COLOR_MUTED, 12, true);
        state.setGravity(Gravity.CENTER);
        card.addView(state);
    }

    private TextView text(int stringRes, int color, int sp, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(stringRes);
        textView.setTextColor(color);
        textView.setTextSize(sp);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
