package net.kdt.pojavlaunch.utils;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageDecoder;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class BattlyBackgrounds {
    public static final String PREF_SELECTED_BACKGROUND = "battly_selected_background";
    public static final String PREF_CUSTOM_BACKGROUND_PATH = "battly_custom_background_path";
    public static final String PREF_CUSTOM_BACKGROUND_MIME = "battly_custom_background_mime";
    private static final String PREF_ONBOARDING_PROMPTED = "battly_background_onboarding_prompted";
    private static final String DEFAULT_KEY = "default";
    private static final String ANIMATED_KEY = "animated_plus";
    private static final String CUSTOM_FILE_KEY = "custom_file";

    private static final BackgroundOption[] OPTIONS = new BackgroundOption[] {
            new BackgroundOption(DEFAULT_KEY, R.drawable.background, 0),
            new BackgroundOption("custom_01", R.drawable.bg_battly_custom_01, 1),
            new BackgroundOption("custom_02", R.drawable.bg_battly_custom_02, 2),
            new BackgroundOption("custom_03", R.drawable.bg_battly_custom_03, 3),
            new BackgroundOption("custom_04", R.drawable.bg_battly_custom_04, 4),
            new BackgroundOption("custom_05", R.drawable.bg_battly_custom_05, 5),
            new BackgroundOption("custom_06", R.drawable.bg_battly_custom_06, 6),
            new BackgroundOption("custom_07", R.drawable.bg_battly_custom_07, 7),
            new BackgroundOption("custom_08", R.drawable.bg_battly_custom_08, 8),
            new BackgroundOption(ANIMATED_KEY, R.drawable.bg_battly_custom_06, -1, true, true, false),
            new BackgroundOption(CUSTOM_FILE_KEY, R.drawable.background, -2, true, false, true)
    };

    private static final int[] ANIMATED_RESOURCES = new int[] {
            R.drawable.background,
            R.drawable.bg_battly_custom_01,
            R.drawable.bg_battly_custom_02,
            R.drawable.bg_battly_custom_04,
            R.drawable.bg_battly_custom_06,
            R.drawable.bg_battly_custom_08
    };

    private BattlyBackgrounds() {
    }

    public interface PlusActionHandler {
        void onPickCustomBackground();
    }

    public static void applySelectedBackground(Context context, @Nullable ImageView target) {
        if (target == null) {
            return;
        }
        String selectedKey = getSelectedKey(context);
        if (CUSTOM_FILE_KEY.equals(selectedKey) && BattlyPlusManager.isPlus(context)) {
            File customFile = getCustomBackgroundFile(context);
            if (customFile.exists()) {
                if (isCustomVideoSelected(context)) {
                    target.setImageResource(R.drawable.background);
                    return;
                }
                if (isCustomGifSelected(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        Drawable drawable = ImageDecoder.decodeDrawable(
                                ImageDecoder.createSource(customFile));
                        target.setImageDrawable(drawable);
                        return;
                    } catch (Throwable ignored) {
                        // Fall through to normal ImageView URI handling.
                    }
                }
                target.setImageURI(Uri.fromFile(customFile));
                return;
            }
        }
        target.setImageResource(getSelectedBackgroundRes(context));
    }

    public static int getSelectedBackgroundRes(Context context) {
        String selectedKey = getSelectedKey(context);
        for (BackgroundOption option : OPTIONS) {
            if (option.key.equals(selectedKey)) {
                return option.drawableRes;
            }
        }
        return R.drawable.background;
    }

    public static int[] getAnimatedBackgroundResources() {
        return ANIMATED_RESOURCES.clone();
    }

    public static boolean isAnimatedSelected(Context context) {
        return ANIMATED_KEY.equals(getSelectedKey(context)) && BattlyPlusManager.isPlus(context);
    }

    public static boolean isCustomSelected(Context context) {
        return CUSTOM_FILE_KEY.equals(getSelectedKey(context)) && BattlyPlusManager.isPlus(context)
                && getCustomBackgroundFile(context).exists();
    }

    public static boolean isCustomVideoSelected(Context context) {
        return isCustomSelected(context) && getCustomBackgroundMime(context).startsWith("video/");
    }

    public static boolean isCustomGifSelected(Context context) {
        String mime = getCustomBackgroundMime(context);
        return isCustomSelected(context)
                && ("image/gif".equalsIgnoreCase(mime)
                || getCustomBackgroundFile(context).getName().toLowerCase(Locale.ROOT).endsWith(".gif"));
    }

    public static Uri getCustomBackgroundUri(Context context) {
        return Uri.fromFile(getCustomBackgroundFile(context));
    }

    public static void saveCustomBackground(Context context, Uri sourceUri) throws Exception {
        String mime = context.getContentResolver().getType(sourceUri);
        if (!Tools.isValidString(mime)) {
            mime = "application/octet-stream";
        }
        String extension = extensionFor(context, sourceUri, mime);
        File parent = getCustomBackgroundDirectory(context);
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Could not create backgrounds folder");
        }
        deleteOldCustomBackgrounds(parent);
        File target = new File(parent, "custom-background." + extension);
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             FileOutputStream outputStream = new FileOutputStream(target, false)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("Could not open selected background");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        }
        prefs(context).edit()
                .putString(PREF_SELECTED_BACKGROUND, CUSTOM_FILE_KEY)
                .putString(PREF_CUSTOM_BACKGROUND_PATH, target.getAbsolutePath())
                .putString(PREF_CUSTOM_BACKGROUND_MIME, mime)
                .apply();
    }

    public static boolean wasOnboardingPromptShown(Context context) {
        return prefs(context).getBoolean(PREF_ONBOARDING_PROMPTED, false);
    }

    public static void markOnboardingPromptShown(Context context) {
        prefs(context).edit().putBoolean(PREF_ONBOARDING_PROMPTED, true).apply();
    }

    public static void showSelector(Context context, @Nullable ImageView livePreview) {
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.BattlyDialog).create();
        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 18);
        root.setPadding(padding, padding, padding, padding);
        scrollView.addView(root);

        TextView title = new TextView(context);
        title.setText(R.string.battly_background_title);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText(R.string.battly_background_subtitle);
        subtitle.setTextColor(0xFFC7D4DF);
        subtitle.setTextSize(13);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, dp(context, 4), 0, dp(context, 12));
        root.addView(subtitle, subtitleParams);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(context.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE ? 3 : 2);
        root.addView(grid);

        populateOptions(context, grid, livePreview, dialog::dismiss, true);

        dialog.setView(scrollView);
        dialog.show();
    }

    public static void populateOptions(Context context, GridLayout grid, @Nullable ImageView livePreview,
            @Nullable Runnable afterSelect, boolean showToast) {
        populateOptions(context, grid, livePreview, afterSelect, showToast, null);
    }

    public static void populateOptions(Context context, GridLayout grid, @Nullable ImageView livePreview,
            @Nullable Runnable afterSelect, boolean showToast, @Nullable PlusActionHandler actionHandler) {
        if (grid == null) {
            return;
        }
        grid.removeAllViews();
        int columns = context.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE ? 3 : 2;
        grid.setColumnCount(columns);
        String selectedKey = getSelectedKey(context);
        for (int i = 0; i < OPTIONS.length; i++) {
            BackgroundOption option = OPTIONS[i];
            LinearLayout card = createOptionCard(context, option, option.key.equals(selectedKey));
            GridLayout.LayoutParams cardParams = new GridLayout.LayoutParams();
            cardParams.width = 0;
            cardParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            cardParams.columnSpec = GridLayout.spec(i % columns, 1f);
            cardParams.rowSpec = GridLayout.spec(i / columns);
            cardParams.setMargins(dp(context, 5), dp(context, 5), dp(context, 5), dp(context, 5));
            grid.addView(card, cardParams);

            card.setOnClickListener(v -> {
                if (option.plusOnly && !BattlyPlusManager.isPlus(context)) {
                    BattlyPlusManager.refreshAsync(context, plus -> {
                        if (!plus) {
                            Toast.makeText(context, R.string.battly_plus_required, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        selectOption(context, grid, livePreview, afterSelect, showToast, actionHandler, option);
                    });
                    return;
                }
                selectOption(context, grid, livePreview, afterSelect, showToast, actionHandler, option);
            });
        }
    }

    private static void selectOption(Context context, GridLayout grid, @Nullable ImageView livePreview,
            @Nullable Runnable afterSelect, boolean showToast, @Nullable PlusActionHandler actionHandler,
            BackgroundOption option) {
        if (option.customPicker) {
            if (actionHandler != null) {
                actionHandler.onPickCustomBackground();
            } else {
                Toast.makeText(context, R.string.battly_background_custom_picker_unavailable, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        prefs(context).edit().putString(PREF_SELECTED_BACKGROUND, option.key).apply();
        applySelectedBackground(context, livePreview);
        populateOptions(context, grid, livePreview, null, false, actionHandler);
        if (showToast) {
            Toast.makeText(context, R.string.battly_background_applied, Toast.LENGTH_SHORT).show();
        }
        if (afterSelect != null) {
            afterSelect.run();
        }
    }

    private static LinearLayout createOptionCard(Context context, BackgroundOption option, boolean selected) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 10));
        card.setBackground(makeOptionBackground(context, selected, option.plusOnly));
        card.setClickable(true);
        card.setFocusable(true);

        FrameLayout previewFrame = new FrameLayout(context);
        card.addView(previewFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(context, option.plusOnly ? 92 : 84)));

        ImageView preview = new ImageView(context);
        if (option.customPicker && getCustomBackgroundFile(context).exists() && !isCustomVideoSelected(context)) {
            preview.setImageURI(Uri.fromFile(getCustomBackgroundFile(context)));
        } else {
            preview.setImageResource(option.drawableRes);
        }
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackground(makeRound(context, 18, 0x33122331, 0));
        preview.setClipToOutline(true);
        previewFrame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (option.plusOnly) {
            TextView plusBadge = new TextView(context);
            plusBadge.setText(R.string.battly_plus_title);
            plusBadge.setTextColor(BattlyPlusManager.isPlus(context) ? 0xFF0A2D34 : 0xFFFFD65A);
            plusBadge.setTextSize(10);
            plusBadge.setTypeface(Typeface.DEFAULT_BOLD);
            plusBadge.setGravity(Gravity.CENTER);
            plusBadge.setPadding(dp(context, 9), dp(context, 4), dp(context, 9), dp(context, 4));
            plusBadge.setBackground(makeRound(context, 999,
                    BattlyPlusManager.isPlus(context) ? 0xFF8DEEDC : 0x7713212F,
                    BattlyPlusManager.isPlus(context) ? 0 : 0x66FFD65A));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END);
            badgeParams.setMargins(0, dp(context, 8), dp(context, 8), 0);
            previewFrame.addView(plusBadge, badgeParams);
        }

        if (selected) {
            TextView selectedBadge = new TextView(context);
            selectedBadge.setText(R.string.battly_background_active_badge);
            selectedBadge.setTextColor(0xFF0A2D34);
            selectedBadge.setTextSize(10);
            selectedBadge.setTypeface(Typeface.DEFAULT_BOLD);
            selectedBadge.setGravity(Gravity.CENTER);
            selectedBadge.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
            selectedBadge.setBackground(makeRound(context, 999, 0xFF8DEEDC, 0));
            FrameLayout.LayoutParams selectedParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START);
            selectedParams.setMargins(dp(context, 8), 0, 0, dp(context, 8));
            previewFrame.addView(selectedBadge, selectedParams);
        }

        TextView label = new TextView(context);
        label.setText(getLabel(context, option));
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(13);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setMaxLines(1);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(dp(context, 2), dp(context, 8), dp(context, 2), 0);
        card.addView(label, labelParams);

        if (option.plusOnly) {
            TextView badge = new TextView(context);
            badge.setText(BattlyPlusManager.isPlus(context)
                    ? R.string.battly_plus_included
                    : R.string.battly_plus_locked);
            badge.setTextColor(BattlyPlusManager.isPlus(context) ? 0xFF8BE7D4 : 0xFFFFD65A);
            badge.setTextSize(11);
            badge.setMaxLines(1);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.setMargins(dp(context, 2), dp(context, 2), dp(context, 2), 0);
            card.addView(badge, badgeParams);
            if (!BattlyPlusManager.isPlus(context)) {
                card.setAlpha(0.62f);
            }
        }
        return card;
    }

    private static CharSequence getLabel(Context context, BackgroundOption option) {
        if (option.animated) {
            return context.getString(R.string.battly_background_animated_plus);
        }
        if (option.customPicker) {
            return context.getString(R.string.battly_background_custom_file);
        }
        if (option.number == 0) {
            return context.getString(R.string.battly_background_default);
        }
        return context.getString(R.string.battly_background_custom, option.number);
    }

    private static String getSelectedKey(Context context) {
        String selectedKey = prefs(context).getString(PREF_SELECTED_BACKGROUND, DEFAULT_KEY);
        if ((ANIMATED_KEY.equals(selectedKey) || CUSTOM_FILE_KEY.equals(selectedKey))
                && !BattlyPlusManager.isPlus(context)) {
            return DEFAULT_KEY;
        }
        return selectedKey;
    }

    private static File getCustomBackgroundFile(Context context) {
        String path = prefs(context).getString(PREF_CUSTOM_BACKGROUND_PATH, "");
        if (path != null && !path.trim().isEmpty()) {
            return new File(path);
        }
        return new File(getCustomBackgroundDirectory(context), "custom-background");
    }

    private static File getCustomBackgroundDirectory(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        return new File(base, "backgrounds");
    }

    private static String getCustomBackgroundMime(Context context) {
        String mime = prefs(context).getString(PREF_CUSTOM_BACKGROUND_MIME, "");
        if (Tools.isValidString(mime)) {
            return mime;
        }
        String name = getCustomBackgroundFile(context).getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4") || name.endsWith(".webm") || name.endsWith(".mkv")) {
            return "video/*";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/*";
    }

    private static String extensionFor(Context context, Uri uri, String mime) {
        String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (Tools.isValidString(extension)) {
            return extension;
        }
        String path = uri.getPath();
        int dot = path == null ? -1 : path.lastIndexOf('.');
        if (dot >= 0 && dot < path.length() - 1) {
            return path.substring(dot + 1).replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        }
        if (mime.startsWith("video/")) {
            return "mp4";
        }
        if ("image/gif".equalsIgnoreCase(mime)) {
            return "gif";
        }
        return "png";
    }

    private static void deleteOldCustomBackgrounds(File parent) {
        File[] files = parent == null ? null : parent.listFiles((dir, name) -> name.startsWith("custom-background"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static SharedPreferences prefs(Context context) {
        if (LauncherPreferences.DEFAULT_PREF != null) {
            return LauncherPreferences.DEFAULT_PREF;
        }
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable makeOptionBackground(Context context, boolean selected, boolean plusOnly) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        selected ? 0xD11B3D46 : plusOnly ? 0xC0193040 : 0xAE132232,
                        selected ? 0xB8132232 : 0x99101D2A
                });
        drawable.setCornerRadius(dp(context, 24));
        drawable.setStroke(dp(context, selected ? 2 : 1),
                selected ? 0xFF8DEEDC : plusOnly ? 0x44FFD65A : 0x2CB9D6E8);
        return drawable;
    }

    private static GradientDrawable makeRound(Context context, int radiusDp, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radiusDp));
        if (stroke != 0) {
            drawable.setStroke(dp(context, 1), stroke);
        }
        return drawable;
    }

    private static final class BackgroundOption {
        final String key;
        final int drawableRes;
        final int number;
        final boolean plusOnly;
        final boolean animated;
        final boolean customPicker;

        BackgroundOption(String key, int drawableRes, int number) {
            this(key, drawableRes, number, false, false, false);
        }

        BackgroundOption(String key, int drawableRes, int number, boolean plusOnly, boolean animated,
                         boolean customPicker) {
            this.key = key;
            this.drawableRes = drawableRes;
            this.number = number;
            this.plusOnly = plusOnly;
            this.animated = animated;
            this.customPicker = customPicker;
        }
    }
}
