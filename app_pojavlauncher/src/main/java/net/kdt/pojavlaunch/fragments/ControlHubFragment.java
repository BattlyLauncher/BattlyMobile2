package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
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
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ControlHubFragment extends Fragment {
    public static final String TAG = "ControlHubFragment";

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_MUTED = 0xFFC7D4DF;
    private static final int COLOR_SUBTLE = 0x99C7D4DF;
    private static final int COLOR_ACCENT = 0xFF8DEEDC;
    private static final int COLOR_PANEL = 0xD9142232;

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(14), dp(28), dp(28));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addHero(root);
        addSectionLabel(root);
        addPrimaryActions(root);
        addMapperAction(root);
        return scrollView;
    }

    private void addHero(LinearLayout parent) {
        FrameLayout hero = new FrameLayout(requireContext());
        hero.setPadding(dp(24), dp(22), dp(24), dp(22));
        hero.setBackground(makeGradient(30, 0xE11A2D3A, 0xD20F1A28, COLOR_ACCENT, 0.24f));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        parent.addView(hero, heroParams);

        LinearLayout content = new LinearLayout(requireContext());
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setOrientation(LinearLayout.HORIZONTAL);
        hero.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout copy = new LinearLayout(requireContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1.1f));

        TextView kicker = labelText(R.string.control_hub_kicker);
        copy.addView(kicker);

        TextView title = text(R.string.control_hub_title, COLOR_TEXT, 32, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(8), 0, 0);
        copy.addView(title, titleParams);

        TextView desc = text(R.string.control_hub_desc, COLOR_MUTED, 14, false);
        desc.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(7), dp(18), 0);
        copy.addView(desc, descParams);

        FrameLayout preview = makeControlPreview();
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 0.9f);
        previewParams.setMargins(dp(20), 0, 0, 0);
        content.addView(preview, previewParams);
    }

    private void addSectionLabel(LinearLayout parent) {
        TextView label = labelText(R.string.control_hub_section);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(18), 0, dp(8));
        parent.addView(label, params);
    }

    private void addPrimaryActions(LinearLayout parent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        addAction(row,
                R.drawable.ic_battly_gamepad_line,
                R.string.control_hub_customize_title,
                R.string.control_hub_customize_desc,
                R.string.control_hub_customize_action,
                v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)),
                true,
                true);
        addAction(row,
                R.drawable.minecraft_bookshelf,
                R.string.control_hub_marketplace_title,
                R.string.control_hub_marketplace_desc,
                R.string.control_hub_marketplace_action,
                v -> Tools.swapFragment(requireActivity(), ControlMarketplaceFragment.class,
                        ControlMarketplaceFragment.TAG, null),
                false,
                false);
    }

    private void addMapperAction(LinearLayout parent) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setPadding(dp(18), dp(12), dp(18), dp(12));
        card.setBackground(makeRound(22, 0xAA142332, 0x1F8DEEDC));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> Tools.swapFragment(requireActivity(), GamepadMapperFragment.class,
                GamepadMapperFragment.TAG, null));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(86));
        params.setMargins(0, dp(12), 0, 0);
        parent.addView(card, params);

        card.addView(icon(R.drawable.ic_battly_gamepad_line, 48, 11, 0x2037E9C5));

        LinearLayout texts = new LinearLayout(requireContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMargins(dp(14), 0, dp(10), 0);
        card.addView(texts, textParams);

        texts.addView(text(R.string.control_hub_mapper_title, COLOR_TEXT, 17, true));
        TextView desc = text(R.string.control_hub_mapper_desc, COLOR_SUBTLE, 12, false);
        desc.setMaxLines(1);
        texts.addView(desc);

        ImageView arrow = new ImageView(requireContext());
        arrow.setImageResource(R.drawable.ic_battly_chevron);
        arrow.setRotation(180f);
        arrow.setAlpha(0.82f);
        card.addView(arrow, new LinearLayout.LayoutParams(dp(30), dp(30)));
    }

    private void addAction(LinearLayout parent, int iconRes, int titleRes, int descRes, int actionRes,
                           View.OnClickListener clickListener, boolean primary, boolean addMargin) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(makeGradient(24, primary ? 0xDB1A3A42 : COLOR_PANEL,
                primary ? 0xC7101E2A : 0xC9142232,
                primary ? COLOR_ACCENT : 0x55B9D6E8,
                primary ? 0.36f : 0.16f));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(clickListener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(178), 1f);
        params.setMargins(0, 0, addMargin ? dp(14) : 0, 0);
        parent.addView(card, params);

        LinearLayout top = new LinearLayout(requireContext());
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        top.addView(icon(iconRes, 54, 12, primary ? 0x3337E9C5 : 0x1EFFFFFF));

        TextView action = text(actionRes, primary ? 0xFF0C2430 : COLOR_ACCENT, 12, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(12), dp(7), dp(12), dp(7));
        action.setBackground(makeRound(999, primary ? COLOR_ACCENT : 0x1737E9C5,
                primary ? 0 : 0x448DEEDC));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(dp(12), 0, 0, 0);
        top.addView(action, actionParams);

        Space space = new Space(requireContext());
        card.addView(space, new LinearLayout.LayoutParams(1, 0, 1f));

        TextView title = text(titleRes, COLOR_TEXT, 20, true);
        card.addView(title);

        TextView desc = text(descRes, COLOR_MUTED, 13, false);
        desc.setLineSpacing(dp(2), 1f);
        desc.setMaxLines(2);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(0, dp(5), 0, 0);
        card.addView(desc, descParams);
    }

    private FrameLayout makeControlPreview() {
        FrameLayout preview = new FrameLayout(requireContext());
        preview.setPadding(dp(16), dp(14), dp(16), dp(14));
        preview.setBackground(makeRound(26, 0x9A0D1A24, 0x338DEEDC));

        View strip = new View(requireContext());
        strip.setBackground(makeRound(999, 0x2428E7CA, 0));
        FrameLayout.LayoutParams stripParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46), Gravity.TOP);
        stripParams.setMargins(dp(10), dp(10), dp(10), 0);
        preview.addView(strip, stripParams);

        addPreviewPill(preview, "TAB", Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0);
        addPreviewPill(preview, "ESC", Gravity.TOP | Gravity.CENTER_HORIZONTAL, 86, 18, 0, 0);
        addPreviewCircle(preview, dp(54), Gravity.LEFT | Gravity.BOTTOM, 20, 0, 0, 18, true);
        addPreviewPill(preview, "INV", Gravity.CENTER, 0, 0, 0, 0);
        addPreviewCircle(preview, dp(60), Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 120, 0, true);
        addPreviewPill(preview, "ATK", Gravity.RIGHT | Gravity.TOP, 0, 62, 36, 0);
        addPreviewPill(preview, "USE", Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 32, 24);
        return preview;
    }

    private void addPreviewPill(FrameLayout parent, String value, int gravity,
                                int left, int top, int right, int bottom) {
        TextView pill = new TextView(requireContext());
        pill.setText(value);
        pill.setTextColor(COLOR_TEXT);
        pill.setTextSize(9);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setBackground(makeRound(999, 0x66375B66, 0x558DEEDC));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(26), gravity);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(pill, params);
    }

    private void addPreviewCircle(FrameLayout parent, int size, int gravity,
                                  int left, int top, int right, int bottom, boolean accent) {
        View circle = new View(requireContext());
        circle.setBackground(makeRound(999, accent ? 0xAA62CDBD : 0x66375B66, 0x668DEEDC));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, gravity);
        params.setMargins(dp(left), dp(top), dp(right), dp(bottom));
        parent.addView(circle, params);
    }

    private ImageView icon(int res, int sizeDp, int paddingDp, int fill) {
        ImageView image = new ImageView(requireContext());
        image.setImageResource(res);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        image.setBackground(makeRound(18, fill, 0x228DEEDC));
        image.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return image;
    }

    private TextView labelText(int stringRes) {
        TextView textView = text(stringRes, COLOR_ACCENT, 12, true);
        textView.setAllCaps(true);
        textView.setLetterSpacing(0.08f);
        return textView;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
