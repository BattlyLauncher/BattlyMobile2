package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

public class BattlyPlusWelcomeFragment extends Fragment {
    public static final String TAG = "BattlyPlusWelcomeFragment";
    private static final String PREF_WELCOME_SEEN = "battly_plus_welcome_seen_v1";

    private static Runnable sAfterClose;

    private final int[] mSlideIcons = new int[]{
            R.drawable.logo,
            R.drawable.ic_battly_worlds_line,
            R.drawable.ic_bookshelf,
            R.drawable.minecraft_nether_star,
            R.drawable.minecraft_filled_map,
            R.drawable.minecraft_chiseled_bookshelf,
            R.drawable.logo
    };
    private final int[] mSlideTitles = new int[]{
            R.string.battly_plus_welcome_title,
            R.string.battly_plus_welcome_worlds_title,
            R.string.battly_plus_feature_cloud_sync_title,
            R.string.battly_plus_feature_mod_updates_title,
            R.string.battly_plus_feature_backups_title,
            R.string.battly_plus_feature_queue_title,
            R.string.battly_plus_feature_app_icons_title
    };
    private final int[] mSlideDescriptions = new int[]{
            R.string.battly_plus_welcome_desc,
            R.string.battly_plus_welcome_worlds_desc,
            R.string.battly_plus_feature_cloud_sync_desc,
            R.string.battly_plus_feature_mod_updates_desc,
            R.string.battly_plus_feature_backups_desc,
            R.string.battly_plus_feature_queue_desc_plus,
            R.string.battly_plus_feature_app_icons_desc
    };

    private int mSlideIndex;
    private ImageView mIconView;
    private TextView mTitleView;
    private TextView mDescView;
    private LinearLayout mDotsContainer;
    private View[] mDots;
    private Button mSkipButton;
    private Button mBackButton;
    private Button mNextButton;

    public static void prepare(@Nullable Runnable afterClose) {
        sAfterClose = afterClose;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        content.setPadding(dp(32), dp(24), dp(32), dp(96));
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        mIconView = new ImageView(requireContext());
        mIconView.setBackgroundResource(R.drawable.bg_battly_profile_icon);
        mIconView.setPadding(dp(24), dp(24), dp(24), dp(24));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(124), dp(124));
        iconParams.setMargins(0, 0, 0, dp(30));
        content.addView(mIconView, iconParams);

        mTitleView = new TextView(requireContext());
        mTitleView.setGravity(Gravity.CENTER);
        mTitleView.setTextColor(Color.WHITE);
        mTitleView.setTextSize(27);
        mTitleView.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(mTitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        mDescView = new TextView(requireContext());
        mDescView.setGravity(Gravity.CENTER);
        mDescView.setLineSpacing(dp(4), 1f);
        mDescView.setTextColor(Color.parseColor("#C7D4DF"));
        mDescView.setTextSize(15);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.setMargins(dp(32), dp(12), dp(32), 0);
        content.addView(mDescView, descParams);

        LinearLayout bottomBar = new LinearLayout(requireContext());
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(24), dp(16), dp(24), dp(16));
        bottomBar.setBackgroundColor(Color.parseColor("#E6121E2C"));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bottomBar, bottomParams);

        mSkipButton = makeNavButton(getString(R.string.global_later), false);
        mSkipButton.setOnClickListener(v -> close(false));
        bottomBar.addView(mSkipButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        mBackButton = makeNavButton(getString(R.string.onboarding_action_back), false);
        mBackButton.setOnClickListener(v -> {
            if (mSlideIndex > 0) {
                mSlideIndex--;
                updateSlide(true);
            }
        });
        bottomBar.addView(mBackButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        Space leftSpace = new Space(requireContext());
        bottomBar.addView(leftSpace, new LinearLayout.LayoutParams(0, 1, 1f));

        mDotsContainer = new LinearLayout(requireContext());
        mDotsContainer.setGravity(Gravity.CENTER);
        mDotsContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsParams.setMargins(0, 0, dp(16), 0);
        bottomBar.addView(mDotsContainer, dotsParams);

        mNextButton = makeNavButton(getString(R.string.onboarding_action_start), true);
        mNextButton.setOnClickListener(v -> {
            if (mSlideIndex + 1 < mSlideIcons.length) {
                mSlideIndex++;
                updateSlide(true);
            } else {
                close(true);
            }
        });
        bottomBar.addView(mNextButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        setupDots();
        updateSlide(false);
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(220).start();
        playSlideIntro(80);
    }

    private void setupDots() {
        mDotsContainer.removeAllViews();
        mDots = new View[mSlideIcons.length];
        for (int i = 0; i < mSlideIcons.length; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(8), dp(8));
            params.setMargins(dp(8), 0, dp(8), 0);
            mDotsContainer.addView(dot, params);
            mDots[i] = dot;
        }
    }

    private void updateSlide(boolean animate) {
        mIconView.setImageResource(mSlideIcons[mSlideIndex]);
        mTitleView.setText(mSlideTitles[mSlideIndex]);
        mDescView.setText(mSlideDescriptions[mSlideIndex]);

        mBackButton.setVisibility(mSlideIndex == 0 ? View.GONE : View.VISIBLE);
        mSkipButton.setVisibility(mSlideIndex == mSlideIcons.length - 1 ? View.GONE : View.VISIBLE);
        mNextButton.setText(mSlideIndex == 0
                ? getString(R.string.onboarding_action_start)
                : mSlideIndex == mSlideIcons.length - 1
                ? getString(R.string.battly_plus_welcome_cta)
                : getString(R.string.onboarding_action_next));

        for (int i = 0; i < mDots.length; i++) {
            boolean active = i == mSlideIndex;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) mDots[i].getLayoutParams();
            params.width = dp(active ? 24 : 8);
            params.height = dp(8);
            mDots[i].setLayoutParams(params);
            mDots[i].setBackground(makeDotDrawable(active));
        }

        if (animate) {
            playSlideIntro(0);
        }
    }

    private void playSlideIntro(long startDelay) {
        resetIntroView(mIconView);
        resetIntroView(mTitleView);
        resetIntroView(mDescView);

        mIconView.setAlpha(0f);
        mIconView.setScaleX(0.84f);
        mIconView.setScaleY(0.84f);
        mIconView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(startDelay)
                .setDuration(360)
                .start();
        animateTextIn(mTitleView, startDelay + 80, 18);
        animateTextIn(mDescView, startDelay + 140, 14);
    }

    private void animateTextIn(View view, long startDelay, int translationDp) {
        view.setAlpha(0f);
        view.setTranslationY(dp(translationDp));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(startDelay)
                .setDuration(300)
                .start();
    }

    private void resetIntroView(View view) {
        view.animate().cancel();
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.setTranslationY(0f);
    }

    private void close(boolean openPlusPanel) {
        SharedPreferences prefs = requireActivity().getSharedPreferences(BattlyPlusManager.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_WELCOME_SEEN, true).apply();
        Runnable afterClose = sAfterClose;
        sAfterClose = null;

        FragmentActivity activity = requireActivity();
        if (openPlusPanel) {
            Tools.removeCurrentFragment(activity);
            activity.getSupportFragmentManager().executePendingTransactions();
            Tools.swapFragment(activity, BattlyPlusFragment.class, BattlyPlusFragment.TAG, null);
        } else {
            Tools.removeCurrentFragment(activity);
        }
        if (afterClose != null) {
            afterClose.run();
        }
    }

    private Button makeNavButton(String label, boolean primary) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(primary ? Color.WHITE : Color.parseColor("#C7D4DF"));
        button.setPadding(dp(primary ? 24 : 10), 0, dp(primary ? 24 : 10), 0);
        button.setMinWidth(primary ? dp(136) : dp(86));
        button.setAllCaps(true);
        button.setBackgroundResource(primary
                ? R.drawable.bg_battly_button_primary
                : android.R.color.transparent);
        return button;
    }

    private GradientDrawable makeDotDrawable(boolean active) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(active ? Color.parseColor("#8BE7D4") : Color.parseColor("#55C7D4DF"));
        drawable.setCornerRadius(dp(9));
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
