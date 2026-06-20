package net.kdt.pojavlaunch.onboarding;

public class OnboardingSlide {
    public int titleResId;
    public int descResId;
    public int iconResId;
    public boolean isBackgroundSlide;
    public boolean isControlsSlide;

    public OnboardingSlide(int titleResId, int descResId, int iconResId) {
        this.titleResId = titleResId;
        this.descResId = descResId;
        this.iconResId = iconResId;
        this.isBackgroundSlide = false;
        this.isControlsSlide = false;
    }

    public static OnboardingSlide background(int titleResId, int descResId, int iconResId) {
        OnboardingSlide slide = new OnboardingSlide(titleResId, descResId, iconResId);
        slide.isBackgroundSlide = true;
        return slide;
    }

    public static OnboardingSlide controls(int titleResId, int descResId, int iconResId) {
        OnboardingSlide slide = new OnboardingSlide(titleResId, descResId, iconResId);
        slide.isControlsSlide = true;
        return slide;
    }
}
