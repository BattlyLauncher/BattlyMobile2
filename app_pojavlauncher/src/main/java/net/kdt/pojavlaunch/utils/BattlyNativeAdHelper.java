package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class BattlyNativeAdHelper {
    private static final String TAG = "BattlyAds";

    public interface Callback {
        void onLoaded(NativeAd ad);
        void onFailed();
    }

    private BattlyNativeAdHelper() {
    }

    public static void load(Activity activity, String unitId, Callback callback) {
        if (activity == null || activity.isFinishing() || BattlyPlusManager.isPlus(activity)) {
            Log.d(TAG, "Native ad skipped: unavailable activity or Battly+ account");
            callback.onFailed();
            return;
        }
        PojavApplication.sExecutorService.execute(() ->
                MobileAds.initialize(activity.getApplicationContext(), status ->
                        activity.runOnUiThread(() -> {
                            if (activity.isFinishing() || activity.isDestroyed()) {
                                Log.d(TAG, "Native ad cancelled: activity is closing");
                                callback.onFailed();
                                return;
                            }
                            new AdLoader.Builder(activity, unitId)
                                    .forNativeAd(ad -> activity.runOnUiThread(() -> {
                                        if (activity.isFinishing() || activity.isDestroyed()) ad.destroy();
                                        else callback.onLoaded(ad);
                                    }))
                                    .withAdListener(new com.google.android.gms.ads.AdListener() {
                                        @Override
                                        public void onAdFailedToLoad(@NonNull LoadAdError error) {
                                            Log.w(TAG, "Native ad failed: " + error.getCode() + " "
                                                    + error.getMessage());
                                            activity.runOnUiThread(callback::onFailed);
                                        }
                                    })
                                    .build()
                                    .loadAd(new AdRequest.Builder().build());
                        })));
    }

    public static NativeAdView createCompactView(Context context, NativeAd nativeAd) {
        NativeAdView adView = new NativeAdView(context);
        adView.setBackground(round(context, 12, 0xE61A2A37, 0x443DD8BE));
        int padding = dp(context, 10);
        adView.setPadding(padding, dp(context, 7), padding, dp(context, 7));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        adView.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 52)));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (nativeAd.getIcon() != null) icon.setImageDrawable(nativeAd.getIcon().getDrawable());
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)));
        adView.setIconView(icon);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        copyParams.setMargins(dp(context, 10), 0, dp(context, 10), 0);
        row.addView(copy, copyParams);

        copy.addView(text(context, context.getString(R.string.native_ad_label), 9, true, 0xFF8DEEDC));
        TextView headline = text(context, nativeAd.getHeadline(), 13, true, 0xFFFFFFFF);
        headline.setSingleLine(true);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(headline);
        adView.setHeadlineView(headline);

        TextView body = text(context, nativeAd.getBody(), 10, false, 0xFFC7D4DF);
        body.setSingleLine(true);
        body.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(body);
        adView.setBodyView(body);

        Button action = new Button(context);
        action.setAllCaps(false);
        action.setMinWidth(0);
        action.setMinimumWidth(0);
        action.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        action.setText(nativeAd.getCallToAction());
        action.setTextSize(10);
        action.setTextColor(0xFF0E1B24);
        action.setBackground(round(context, 10, 0xFF8DEEDC, 0));
        row.addView(action, new LinearLayout.LayoutParams(dp(context, 92), dp(context, 38)));
        adView.setCallToActionView(action);
        adView.setNativeAd(nativeAd);
        return adView;
    }

    /** A two-column catalog card matching the dimensions of Workspace content cards. */
    public static NativeAdView createCatalogCardView(Context context, NativeAd nativeAd) {
        NativeAdView adView = new NativeAdView(context);
        adView.setBackground(round(context, 8, 0xE61A2A37, 0x443DD8BE));
        int padding = dp(context, 12);
        adView.setPadding(padding, padding, padding, padding);
        adView.setMinimumHeight(dp(context, 88));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        adView.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (nativeAd.getIcon() != null) icon.setImageDrawable(nativeAd.getIcon().getDrawable());
        row.addView(icon, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        adView.setIconView(icon);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.setMargins(dp(context, 12), 0, dp(context, 8), 0);
        row.addView(copy, copyParams);

        copy.addView(text(context, context.getString(R.string.native_ad_label),
                9, true, 0xFF8DEEDC));
        TextView headline = text(context, nativeAd.getHeadline(), 14, true, 0xFFFFFFFF);
        headline.setSingleLine(true);
        headline.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(headline);
        adView.setHeadlineView(headline);

        TextView body = text(context, nativeAd.getBody(), 11, false, 0xFFC7D4DF);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(body);
        adView.setBodyView(body);

        Button action = new Button(context);
        action.setAllCaps(false);
        action.setMinWidth(0);
        action.setMinimumWidth(0);
        action.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        action.setText(nativeAd.getCallToAction());
        action.setTextSize(10);
        action.setTextColor(0xFF0E1B24);
        action.setBackground(round(context, 8, 0xFF8DEEDC, 0));
        row.addView(action, new LinearLayout.LayoutParams(dp(context, 78), dp(context, 36)));
        adView.setCallToActionView(action);
        adView.setNativeAd(nativeAd);
        return adView;
    }

    public static List<Integer> randomAdPositions(int contentCount, int adCount, long seed) {
        if (contentCount <= 0 || adCount <= 0) return Collections.emptyList();
        int rowCount = (contentCount + 1) / 2;
        ArrayList<Integer> rows = new ArrayList<>();
        for (int row = rowCount > 1 ? 1 : 0; row <= rowCount; row++) rows.add(row);
        Collections.shuffle(rows, new Random(seed));
        rows = new ArrayList<>(rows.subList(0, Math.min(adCount, rows.size())));
        Collections.sort(rows);
        ArrayList<Integer> positions = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            positions.add(Math.min(contentCount + i, rows.get(i) * 2 + i));
        }
        return positions;
    }

    private static TextView text(Context context, String value, int sp, boolean bold, int color) {
        TextView view = new TextView(context);
        view.setText(value == null ? "" : value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable round(Context context, int radius, int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != 0) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
