package net.kdt.pojavlaunch.onboarding;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.BattlyBackgrounds;
import net.kdt.pojavlaunch.utils.BattlyControlLayouts;

import java.io.IOException;
import java.util.List;

public class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.SlideViewHolder> {

    private final List<OnboardingSlide> mSlides;
    private final OnboardingActivity mActivity;

    public OnboardingAdapter(List<OnboardingSlide> slides, OnboardingActivity activity) {
        this.mSlides = slides;
        this.mActivity = activity;
    }

    @NonNull
    @Override
    public SlideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_onboarding_slide, parent, false);
        return new SlideViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideViewHolder holder, int position) {
        holder.bind(mSlides.get(position));
    }

    @Override
    public int getItemCount() {
        return mSlides.size();
    }

    class SlideViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView titleView;
        TextView descView;
        View contentView;
        View backgroundContainer;
        GridLayout backgroundGrid;
        View controlsContainer;
        GridLayout controlsGrid;

        public SlideViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.onboarding_slide_icon);
            titleView = itemView.findViewById(R.id.onboarding_slide_title);
            descView = itemView.findViewById(R.id.onboarding_slide_desc);
            contentView = itemView.findViewById(R.id.onboarding_slide_content);
            backgroundContainer = itemView.findViewById(R.id.onboarding_slide_background_container);
            backgroundGrid = itemView.findViewById(R.id.onboarding_background_grid);
            controlsContainer = itemView.findViewById(R.id.onboarding_slide_controls_container);
            controlsGrid = itemView.findViewById(R.id.onboarding_controls_grid);
        }

        public void bind(OnboardingSlide slide) {
            iconView.setImageResource(slide.iconResId);
            titleView.setText(slide.titleResId);
            descView.setText(slide.descResId);
            boolean interactiveSlide = slide.isBackgroundSlide || slide.isControlsSlide;
            iconView.setVisibility(interactiveSlide ? View.GONE : View.VISIBLE);
            if (contentView != null) {
                boolean landscape = itemView.getContext().getResources()
                        .getConfiguration().orientation == ORIENTATION_LANDSCAPE;
                contentView.setPadding(
                        contentView.getPaddingLeft(),
                        contentView.getPaddingTop(),
                        interactiveSlide && landscape ? 0 : dp(itemView.getContext(), 32),
                        contentView.getPaddingBottom());
            }

            if (slide.isBackgroundSlide && backgroundContainer != null) {
                backgroundContainer.setVisibility(View.VISIBLE);
                BattlyBackgrounds.populateOptions(itemView.getContext(), backgroundGrid,
                        mActivity.getBackgroundPreview(), null, true);
            } else if (backgroundContainer != null) {
                backgroundContainer.setVisibility(View.GONE);
            }

            if (slide.isControlsSlide && controlsContainer != null) {
                controlsContainer.setVisibility(View.VISIBLE);
                populateControlOptions();
            } else if (controlsContainer != null) {
                controlsContainer.setVisibility(View.GONE);
            }

            playSlideIntro(interactiveSlide);
        }

        private void playSlideIntro(boolean interactiveSlide) {
            resetIntroView(iconView);
            resetIntroView(titleView);
            resetIntroView(descView);
            resetIntroView(backgroundContainer);
            resetIntroView(controlsContainer);

            if (!interactiveSlide) {
                iconView.setAlpha(0f);
                iconView.setScaleX(0.84f);
                iconView.setScaleY(0.84f);
                iconView.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay(40)
                        .setDuration(360)
                        .start();
            }

            animateTextIn(titleView, 90, 18);
            animateTextIn(descView, 150, 14);

            View activePanel = backgroundContainer != null && backgroundContainer.getVisibility() == View.VISIBLE
                    ? backgroundContainer
                    : controlsContainer != null && controlsContainer.getVisibility() == View.VISIBLE
                            ? controlsContainer
                            : null;
            if (activePanel != null) {
                activePanel.setAlpha(0f);
                activePanel.setTranslationY(dp(itemView.getContext(), 16));
                activePanel.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setStartDelay(210)
                        .setDuration(340)
                        .start();
            }
        }

        private void animateTextIn(View view, long startDelay, int translationDp) {
            if (view == null) return;
            view.setAlpha(0f);
            view.setTranslationY(dp(itemView.getContext(), translationDp));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(startDelay)
                    .setDuration(300)
                    .start();
        }

        private void resetIntroView(View view) {
            if (view == null) return;
            view.animate().cancel();
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setTranslationY(0f);
        }

        private void populateControlOptions() {
            if (controlsGrid == null) {
                return;
            }
            controlsGrid.removeAllViews();
            controlsGrid.addView(createControlCard(
                    BattlyControlLayouts.CLASSIC,
                    itemView.getContext().getString(R.string.onboarding_controls_classic_title),
                    itemView.getContext().getString(R.string.onboarding_controls_classic_desc),
                    false));
            controlsGrid.addView(createControlCard(
                    BattlyControlLayouts.MODERN,
                    itemView.getContext().getString(R.string.onboarding_controls_modern_title),
                    itemView.getContext().getString(R.string.onboarding_controls_modern_desc),
                    true));
        }

        private View createControlCard(String layout, String title, String description, boolean modern) {
            Context context = itemView.getContext();
            boolean selected = BattlyControlLayouts.isSelected(layout);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(context, 10), dp(context, 10), dp(context, 10), dp(context, 10));
            card.setBackground(cardBackground(context, selected));
            card.setClickable(true);
            card.setFocusable(true);

            GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
            gridParams.width = 0;
            gridParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            gridParams.setMargins(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
            card.setLayoutParams(gridParams);

            FrameLayout preview = createControlsPreview(context, modern);
            card.addView(preview);

            TextView titleView = new TextView(context);
            titleView.setText(selected
                    ? context.getString(R.string.onboarding_controls_selected, title)
                    : title);
            titleView.setTextColor(Color.WHITE);
            titleView.setTextSize(14);
            titleView.setTypeface(Typeface.DEFAULT_BOLD);
            titleView.setPadding(0, dp(context, 8), 0, 0);
            card.addView(titleView);

            TextView descView = new TextView(context);
            descView.setText(description);
            descView.setTextColor(Color.parseColor("#BFD0DA"));
            descView.setTextSize(11);
            descView.setLineSpacing(dp(context, 2), 1f);
            descView.setMaxLines(2);
            descView.setPadding(0, dp(context, 3), 0, 0);
            card.addView(descView);

            card.setOnClickListener(v -> {
                try {
                    BattlyControlLayouts.apply(context, layout);
                    Toast.makeText(context, R.string.onboarding_controls_applied, Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                } catch (IOException e) {
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            return card;
        }

        private FrameLayout createControlsPreview(Context context, boolean modern) {
            FrameLayout preview = new FrameLayout(context);
            preview.setBackground(previewBackground(context));
            preview.setClipToOutline(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(context, 116));
            preview.setLayoutParams(params);

            ImageView screenshot = new ImageView(context);
            screenshot.setImageResource(modern
                    ? R.drawable.preview_controls_modern
                    : R.drawable.preview_controls_classic);
            screenshot.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.addView(screenshot, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            View shade = new View(context);
            shade.setBackgroundColor(Color.parseColor("#22061116"));
            preview.addView(shade, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return preview;
        }

        private void addPreviewTopBar(Context context, FrameLayout preview) {
            LinearLayout bar = new LinearLayout(context);
            bar.setGravity(android.view.Gravity.CENTER);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setBackground(previewBarBackground(context));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(context, 144),
                    dp(context, 22),
                    android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL);
            params.topMargin = dp(context, 8);
            preview.addView(bar, params);

            String[] icons = {"", "", "", ""};
            for (String icon : icons) {
                TextView chip = new TextView(context);
                chip.setText(icon);
                chip.setTextColor(Color.parseColor("#D7EEF0"));
                chip.setTextSize(6);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setBackground(previewButtonBackground(context, false, 8));
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                        dp(context, 18),
                        dp(context, 14));
                chipParams.setMargins(dp(context, 3), 0, dp(context, 3), 0);
                bar.addView(chip, chipParams);
            }
        }

        private void addPreviewHotbar(Context context, FrameLayout preview) {
            LinearLayout hotbar = new LinearLayout(context);
            hotbar.setGravity(android.view.Gravity.CENTER);
            hotbar.setOrientation(LinearLayout.HORIZONTAL);
            hotbar.setBackground(previewBarBackground(context));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(context, 126),
                    dp(context, 18),
                    android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL);
            params.bottomMargin = dp(context, 7);
            preview.addView(hotbar, params);

            for (int i = 0; i < 9; i++) {
                TextView slot = new TextView(context);
                slot.setBackground(slotBackground(context, i == 0));
                LinearLayout.LayoutParams slotParams = new LinearLayout.LayoutParams(
                        dp(context, 12),
                        dp(context, 12));
                slotParams.setMargins(dp(context, 1), 0, dp(context, 1), 0);
                hotbar.addView(slot, slotParams);
            }
        }

        private void addPreviewButton(Context context, FrameLayout preview, int left, int top, int width, int height,
                                      String text, boolean accent, boolean round) {
            TextView button = new TextView(context);
            button.setText(text);
            button.setGravity(android.view.Gravity.CENTER);
            button.setTextColor(Color.WHITE);
            button.setTextSize(8.5f);
            button.setTypeface(Typeface.DEFAULT_BOLD);
            button.setBackground(previewButtonBackground(context, accent, round ? height : 10));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    dp(context, width),
                    dp(context, height));
            params.leftMargin = dp(context, left);
            params.topMargin = dp(context, top);
            preview.addView(button, params);
        }

        private GradientDrawable cardBackground(Context context, boolean selected) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor(selected ? "#263F46" : "#1E2C36"));
            drawable.setCornerRadius(dp(context, 18));
            drawable.setStroke(dp(context, selected ? 2 : 1),
                    Color.parseColor(selected ? "#8BE7D4" : "#334A56"));
            return drawable;
        }

        private GradientDrawable previewBackground(Context context) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor("#14222B"));
            drawable.setCornerRadius(dp(context, 14));
            drawable.setStroke(dp(context, 1), Color.parseColor("#28414D"));
            return drawable;
        }

        private GradientDrawable previewButtonBackground(Context context, boolean accent, int radius) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor(accent ? "#5DBAA8" : "#315A67"));
            drawable.setCornerRadius(dp(context, radius));
            drawable.setStroke(dp(context, 1), Color.parseColor(accent ? "#A4F5E8" : "#62818C"));
            return drawable;
        }

        private GradientDrawable previewBarBackground(Context context) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor("#AA0D1820"));
            drawable.setCornerRadius(dp(context, 7));
            drawable.setStroke(dp(context, 1), Color.parseColor("#35535F"));
            return drawable;
        }

        private GradientDrawable slotBackground(Context context, boolean selected) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.parseColor(selected ? "#5D746F" : "#26343B"));
            drawable.setStroke(dp(context, 1), Color.parseColor(selected ? "#E8FFF8" : "#405763"));
            return drawable;
        }

        private int dp(Context context, int value) {
            return Math.round(value * context.getResources().getDisplayMetrics().density);
        }
    }
}
