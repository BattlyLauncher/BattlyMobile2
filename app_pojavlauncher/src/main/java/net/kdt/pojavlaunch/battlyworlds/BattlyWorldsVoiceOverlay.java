package net.kdt.pojavlaunch.battlyworlds;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

final class BattlyWorldsVoiceOverlay {
    private static final long HIDE_DELAY_MS = 420L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, OverlayState> STATES = new WeakHashMap<>();

    static void setSpeaking(Activity activity, String userId, String username, boolean speaking) {
        if (activity == null || activity.isFinishing() || userId == null || userId.isEmpty()) return;
        if (!BattlyWorldsPreferences.isVoiceOverlayEnabled(activity)) {
            clear(activity);
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.isDestroyed()) return;
            OverlayState state = state(activity);
            SpeakerRow row = state.rows.get(userId);
            if (speaking) {
                if (row == null) {
                    row = new SpeakerRow(activity, username);
                    state.rows.put(userId, row);
                    state.container.addView(row.root, 0, row.params(activity));
                    attachDrag(state, row.root);
                    row.show();
                } else {
                    row.setUsername(username);
                }
                row.setSpeaking(true);
                state.container.post(() -> placeFromPreferences(activity, state));
            } else if (row != null) {
                row.setSpeaking(false);
                SpeakerRow current = row;
                MAIN.postDelayed(() -> {
                    if (current.speaking || state.rows.get(userId) != current) return;
                    state.rows.remove(userId);
                    current.hide();
                }, HIDE_DELAY_MS);
            }
        });
    }

    static void setConnectionBlocked(Activity activity, boolean blocked) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            OverlayState state = STATES.get(activity);
            if (state == null) return;
            state.blocked = blocked;
            applyVisibility(state);
        });
    }

    static void refreshAppearance(Activity activity) {
        if (activity == null) return;
        if (!BattlyWorldsPreferences.isVoiceOverlayEnabled(activity)) {
            clear(activity);
            return;
        }
        activity.runOnUiThread(() -> {
            OverlayState state = STATES.get(activity);
            if (state == null) return;
            state.opacity = BattlyWorldsPreferences.getVoiceOverlayOpacity(activity) / 100f;
            applyVisibility(state);
            placeFromPreferences(activity, state);
        });
    }

    static void resetPosition(Activity activity) {
        if (activity == null) return;
        BattlyWorldsPreferences.resetVoiceOverlayPosition(activity);
        refreshAppearance(activity);
    }

    static FrameLayout createPositionEditor(Activity activity, String username) {
        FrameLayout editor = new FrameLayout(activity);
        editor.setClipChildren(false);
        editor.setBackground(editorBackground());
        SpeakerRow preview = new SpeakerRow(activity, username);
        FrameLayout.LayoutParams rowParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        editor.addView(preview.root, rowParams);
        editor.post(() -> placeEditorPreview(activity, editor, preview.root));
        preview.root.setOnTouchListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    view.setTag(new float[]{event.getRawX(), event.getRawY(), view.getX(), view.getY()});
                    view.animate().scaleX(1.04f).scaleY(1.04f).setDuration(90L).start();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float[] start = (float[]) view.getTag();
                    if (start == null) return false;
                    float maxX = Math.max(0, editor.getWidth() - view.getWidth());
                    float maxY = Math.max(0, editor.getHeight() - view.getHeight());
                    view.setX(clamp(start[2] + event.getRawX() - start[0], 0, maxX));
                    view.setY(clamp(start[3] + event.getRawY() - start[1], 0, maxY));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                    saveEditorPosition(activity, editor, view);
                    return true;
                default:
                    return false;
            }
        });
        return editor;
    }

    private static void placeEditorPreview(Activity activity, FrameLayout editor, View preview) {
        int maxX = Math.max(0, editor.getWidth() - preview.getWidth());
        int maxY = Math.max(0, editor.getHeight() - preview.getHeight());
        preview.setX(maxX * BattlyWorldsPreferences.getVoiceOverlayX(activity) / 1000f);
        preview.setY(maxY * BattlyWorldsPreferences.getVoiceOverlayY(activity) / 1000f);
    }

    private static void saveEditorPosition(Activity activity, FrameLayout editor, View preview) {
        float maxX = Math.max(1, editor.getWidth() - preview.getWidth());
        float maxY = Math.max(1, editor.getHeight() - preview.getHeight());
        BattlyWorldsPreferences.setVoiceOverlayPosition(activity,
                Math.round(preview.getX() / maxX * 1000f),
                Math.round(preview.getY() / maxY * 1000f));
        refreshAppearance(activity);
    }

    static void clear(Activity activity) {
        if (activity == null) return;
        activity.runOnUiThread(() -> {
            OverlayState state = STATES.remove(activity);
            if (state == null) return;
            for (SpeakerRow row : state.rows.values()) row.dispose();
            state.rows.clear();
            if (state.container.getParent() instanceof ViewGroup) {
                ((ViewGroup) state.container.getParent()).removeView(state.container);
            }
        });
    }

    private static OverlayState state(Activity activity) {
        OverlayState existing = STATES.get(activity);
        if (existing != null && existing.container.getParent() != null) return existing;
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setGravity(Gravity.END);
        container.setClipChildren(false);
        container.setClipToPadding(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        ((ViewGroup) activity.getWindow().getDecorView()).addView(container, params);
        OverlayState created = new OverlayState(container,
                BattlyWorldsPreferences.getVoiceOverlayOpacity(activity) / 100f);
        created.blocked = BattlyWorldsPresenceOverlay.isConnectionInProgress(activity);
        STATES.put(activity, created);
        container.post(() -> placeFromPreferences(activity, created));
        applyVisibility(created);
        return created;
    }

    private static void placeFromPreferences(Activity activity, OverlayState state) {
        View decor = activity.getWindow().getDecorView();
        int maxX = Math.max(0, decor.getWidth() - state.container.getWidth() - dp(activity, 8));
        int maxY = Math.max(0, decor.getHeight() - state.container.getHeight() - dp(activity, 8));
        state.container.setX(dp(activity, 8)
                + maxX * BattlyWorldsPreferences.getVoiceOverlayX(activity) / 1000f);
        state.container.setY(dp(activity, 8)
                + maxY * BattlyWorldsPreferences.getVoiceOverlayY(activity) / 1000f);
    }

    private static void attachDrag(OverlayState state, View handle) {
        handle.setOnTouchListener((view, event) -> {
            View decor = state.container.getRootView();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    state.dragRawX = event.getRawX();
                    state.dragRawY = event.getRawY();
                    state.dragStartX = state.container.getX();
                    state.dragStartY = state.container.getY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float maxX = Math.max(0, decor.getWidth() - state.container.getWidth());
                    float maxY = Math.max(0, decor.getHeight() - state.container.getHeight());
                    state.container.setX(clamp(state.dragStartX + event.getRawX() - state.dragRawX, 0, maxX));
                    state.container.setY(clamp(state.dragStartY + event.getRawY() - state.dragRawY, 0, maxY));
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    savePosition(view, state, decor);
                    return true;
                default:
                    return false;
            }
        });
    }

    private static void savePosition(View view, OverlayState state, View decor) {
        float maxX = Math.max(1, decor.getWidth() - state.container.getWidth());
        float maxY = Math.max(1, decor.getHeight() - state.container.getHeight());
        BattlyWorldsPreferences.setVoiceOverlayPosition(view.getContext(),
                Math.round(state.container.getX() / maxX * 1000f),
                Math.round(state.container.getY() / maxY * 1000f));
    }

    private static void applyVisibility(OverlayState state) {
        state.container.animate().cancel();
        state.container.animate().alpha(state.blocked ? 0f : state.opacity).setDuration(160L)
                .withStartAction(() -> state.container.setVisibility(View.VISIBLE))
                .withEndAction(() -> {
                    if (state.blocked) state.container.setVisibility(View.INVISIBLE);
                }).start();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static final class OverlayState {
        final LinearLayout container;
        final Map<String, SpeakerRow> rows = new LinkedHashMap<>();
        float opacity;
        boolean blocked;
        float dragRawX;
        float dragRawY;
        float dragStartX;
        float dragStartY;
        OverlayState(LinearLayout container, float opacity) {
            this.container = container;
            this.opacity = opacity;
        }
    }

    private static final class SpeakerRow {
        final LinearLayout root;
        final FrameLayout avatarFrame;
        final TextView name;
        final ValueAnimator pulse;
        boolean speaking;

        SpeakerRow(Activity activity, String username) {
            root = new LinearLayout(activity);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(activity, 5), dp(activity, 4), dp(activity, 9), dp(activity, 4));
            root.setBackground(cardBackground());
            root.setElevation(dp(activity, 7));

            avatarFrame = new FrameLayout(activity);
            avatarFrame.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));
            avatarFrame.setBackground(speakingBorder(0xFF72E3BE));
            ImageView avatar = new ImageView(activity);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setClipToOutline(true);
            avatar.setBackground(avatarBackground());
            avatarFrame.addView(avatar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(avatarFrame, new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30)));
            BattlyWorldsAvatarLoader.load(avatar, username);

            name = new TextView(activity);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(12);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setPadding(dp(activity, 8), 0, 0, 0);
            root.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            setUsername(username);

            pulse = ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(720L);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.addUpdateListener(animation -> {
                float value = (float) animation.getAnimatedValue();
                int alpha = 170 + Math.round(value * 85);
                avatarFrame.setBackground(speakingBorder((alpha << 24) | 0x0072E3BE));
                root.setScaleX(1f + value * 0.012f);
                root.setScaleY(1f + value * 0.012f);
            });
        }

        LinearLayout.LayoutParams params(Activity activity) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(activity, 5);
            params.gravity = Gravity.END;
            return params;
        }

        void setUsername(String username) {
            name.setText(username == null || username.trim().isEmpty() ? "BattlyPlayer" : username);
        }

        void setSpeaking(boolean value) {
            speaking = value;
            if (value && !pulse.isStarted()) pulse.start();
            if (!value) {
                pulse.cancel();
                root.setScaleX(1f);
                root.setScaleY(1f);
            }
        }

        void show() {
            root.setAlpha(0f);
            root.setTranslationX(dp(root.getContext(), 70));
            root.animate().alpha(1f).translationX(0f).setDuration(180L).start();
        }

        void hide() {
            dispose();
            root.animate().alpha(0f).translationX(dp(root.getContext(), 70)).setDuration(190L)
                    .withEndAction(() -> {
                        if (root.getParent() instanceof ViewGroup) {
                            ((ViewGroup) root.getParent()).removeView(root);
                        }
                    }).start();
        }

        void dispose() {
            pulse.cancel();
            root.animate().cancel();
        }
    }

    private static GradientDrawable cardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xF01A2932);
        drawable.setCornerRadius(18);
        drawable.setStroke(1, 0x774D6A75);
        return drawable;
    }

    private static GradientDrawable avatarBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xFF233843);
        drawable.setCornerRadius(7);
        return drawable;
    }

    private static GradientDrawable speakingBorder(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00203038);
        drawable.setCornerRadius(9);
        drawable.setStroke(2, color);
        return drawable;
    }

    private static GradientDrawable editorBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x66101B22);
        drawable.setCornerRadius(14);
        drawable.setStroke(1, 0x668ADBC6);
        return drawable;
    }

    private static int dp(android.content.Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private BattlyWorldsVoiceOverlay() {
    }
}
