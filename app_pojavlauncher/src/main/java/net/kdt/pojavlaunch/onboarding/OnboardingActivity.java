package net.kdt.pojavlaunch.onboarding;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.viewpager2.widget.ViewPager2;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.BattlyBackgrounds;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {
    public static final String EXTRA_WHATS_NEW = "battly_whats_new";
    public static final String PREF_WHATS_NEW_SEEN = "battly_whats_new_seen_2_0_1_management_v1";

    private ViewPager2 viewPager;
    private OnboardingAdapter adapter;
    private LinearLayout dotsContainer;
    private ImageView backgroundView;
    private Button btnSkip, btnBack, btnNext;
    private View bottomBar;
    private View welcomeTransition;
    private View transitionCard;
    private ImageView transitionLogo;
    private List<OnboardingSlide> slides;
    private boolean finishingOnboarding;
    private boolean whatsNewMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen / immersive
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowInsetsControllerCompat insetsController =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetsController.hide(WindowInsetsCompat.Type.systemBars());
        insetsController.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        setContentView(R.layout.activity_battly_onboarding);
        whatsNewMode = getIntent().getBooleanExtra(EXTRA_WHATS_NEW, false);

        backgroundView = findViewById(R.id.onboarding_background);
        viewPager = findViewById(R.id.onboarding_viewpager);
        dotsContainer = findViewById(R.id.onboarding_dots_container);
        btnSkip = findViewById(R.id.onboarding_btn_skip);
        btnBack = findViewById(R.id.onboarding_btn_back);
        btnNext = findViewById(R.id.onboarding_btn_next);
        bottomBar = findViewById(R.id.onboarding_bottom_bar);
        welcomeTransition = findViewById(R.id.onboarding_welcome_transition);
        transitionCard = findViewById(R.id.onboarding_transition_card);
        transitionLogo = findViewById(R.id.onboarding_transition_logo);
        if (whatsNewMode) {
            ((android.widget.TextView) findViewById(R.id.onboarding_transition_title))
                    .setText(R.string.battly_whats_new_transition_title);
            ((android.widget.TextView) findViewById(R.id.onboarding_transition_desc))
                    .setText(R.string.battly_whats_new_transition_desc);
        }
        BattlyBackgrounds.applySelectedBackground(this, backgroundView);

        setupSlides();

        adapter = new OnboardingAdapter(slides, this);
        viewPager.setAdapter(adapter);

        setupDots();
        updateButtons(0);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots(position);
                updateButtons(position);
            }
        });

        btnSkip.setOnClickListener(v -> finishOnboarding());
        
        btnBack.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() > 0) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() - 1);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() + 1 < slides.size()) {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            } else {
                finishOnboarding();
            }
        });
    }

    private void setupSlides() {
        slides = new ArrayList<>();
        if (whatsNewMode) {
            slides.add(new OnboardingSlide(R.string.battly_whats_new_title,
                    R.string.battly_whats_new_desc, R.drawable.logo));
            slides.add(new OnboardingSlide(R.string.battly_whats_new_instances_title,
                    R.string.battly_whats_new_instances_desc, R.drawable.minecraft_chest));
            slides.add(new OnboardingSlide(R.string.battly_whats_new_worlds_title,
                    R.string.battly_whats_new_worlds_desc, R.drawable.minecraft_filled_map));
            slides.add(new OnboardingSlide(R.string.battly_whats_new_compatibility_title,
                    R.string.battly_whats_new_compatibility_desc, R.drawable.minecraft_book));
            slides.add(new OnboardingSlide(R.string.battly_whats_new_controllers_title,
                    R.string.battly_whats_new_controllers_desc, R.drawable.ic_battly_gamepad_line));
            slides.add(new OnboardingSlide(R.string.battly_whats_new_recovery_title,
                    R.string.battly_whats_new_recovery_desc, R.drawable.minecraft_diamond_pickaxe));
            return;
        }
        slides.add(new OnboardingSlide(R.string.onboarding_welcome_title, R.string.onboarding_welcome_desc, R.drawable.logo));
        slides.add(new OnboardingSlide(R.string.onboarding_library_title, R.string.onboarding_library_desc, R.drawable.minecraft_bookshelf));
        slides.add(new OnboardingSlide(R.string.onboarding_versions_title, R.string.onboarding_versions_desc, R.drawable.minecraft_book));
        slides.add(new OnboardingSlide(R.string.onboarding_profiles_title, R.string.onboarding_profiles_desc, R.drawable.minecraft_oak_sign));
        slides.add(OnboardingSlide.controls(R.string.onboarding_controls_title, R.string.onboarding_controls_desc, R.drawable.ic_battly_gamepad_line));
        slides.add(OnboardingSlide.background(R.string.onboarding_background_title, R.string.onboarding_background_desc, R.drawable.minecraft_filled_map));
        slides.add(new OnboardingSlide(R.string.onboarding_finish_title, R.string.onboarding_finish_desc, R.drawable.minecraft_diamond_pickaxe));
    }

    public ImageView getBackgroundPreview() {
        return backgroundView;
    }

    private void setupDots() {
        ImageView[] dots = new ImageView[slides.size()];
        for (int i = 0; i < slides.size(); i++) {
            dots[i] = new ImageView(this);
            dots[i].setImageResource(R.drawable.bg_battly_version_dot);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMargins(8, 0, 8, 0);
            dots[i].setLayoutParams(params);
            dots[i].setAlpha(0.3f);
            dotsContainer.addView(dots[i]);
        }
        if (dots.length > 0) {
            dots[0].setAlpha(1.0f);
        }
    }

    private void updateDots(int position) {
        int childCount = dotsContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView dot = (ImageView) dotsContainer.getChildAt(i);
            if (i == position) {
                dot.setAlpha(1.0f);
            } else {
                dot.setAlpha(0.3f);
            }
        }
    }

    private void updateButtons(int position) {
        if (position == 0) {
            btnBack.setVisibility(View.GONE);
            btnSkip.setVisibility(View.VISIBLE);
            btnNext.setText(whatsNewMode
                    ? R.string.battly_whats_new_action_start
                    : R.string.onboarding_action_start);
        } else if (position == slides.size() - 1) {
            btnBack.setVisibility(View.VISIBLE);
            btnSkip.setVisibility(View.GONE);
            btnNext.setText(whatsNewMode
                    ? R.string.battly_update_video_continue
                    : R.string.onboarding_action_enter);
        } else {
            btnBack.setVisibility(View.VISIBLE);
            btnSkip.setVisibility(View.VISIBLE);
            btnNext.setText(R.string.onboarding_action_next);
        }
    }

    private void finishOnboarding() {
        if (finishingOnboarding) {
            return;
        }
        finishingOnboarding = true;
        btnSkip.setEnabled(false);
        btnBack.setEnabled(false);
        btnNext.setEnabled(false);
        playWelcomeTransition();
        new Handler(Looper.getMainLooper()).postDelayed(this::doFinishOnboarding, 1250);
    }

    private void playWelcomeTransition() {
        if (welcomeTransition == null) {
            return;
        }

        welcomeTransition.setVisibility(View.VISIBLE);
        welcomeTransition.setAlpha(0f);
        if (transitionCard != null) {
            transitionCard.setAlpha(0f);
            transitionCard.setScaleX(0.9f);
            transitionCard.setScaleY(0.9f);
            transitionCard.setTranslationY(40f);
        }
        if (transitionLogo != null) {
            transitionLogo.setAlpha(0f);
            transitionLogo.setScaleX(0.72f);
            transitionLogo.setScaleY(0.72f);
            transitionLogo.setRotation(-8f);
        }

        viewPager.animate().alpha(0f).setDuration(220).start();
        bottomBar.animate().alpha(0f).translationY(bottomBar.getHeight() / 3f).setDuration(220).start();
        welcomeTransition.animate().alpha(1f).setDuration(260).start();

        if (transitionCard != null) {
            transitionCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay(120)
                    .setDuration(420)
                    .start();
        }
        if (transitionLogo != null) {
            transitionLogo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .rotation(0f)
                    .setStartDelay(180)
                    .setDuration(500)
                    .start();
        }
    }

    private void doFinishOnboarding() {
        if (whatsNewMode) {
            LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF_WHATS_NEW_SEEN, true).apply();
            setResult(RESULT_OK);
            finish();
            return;
        }
        LauncherPreferences.PREF_BATTLY_ONBOARDING_COMPLETED = true;
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean("battly_onboarding_completed", true).apply();

        Intent intent = new Intent(this, LauncherActivity.class);
        startActivity(intent);
        finish();
    }
}
