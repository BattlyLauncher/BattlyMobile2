package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;

import net.kdt.pojavlaunch.PojavApplication;

import java.util.concurrent.atomic.AtomicBoolean;

/** Loads Google full-screen ads in the launcher process, where Google AdActivity also runs. */
public final class BattlyRewardedInterstitialActivity extends Activity {
    private static final String TAG = "BattlyRewardedAd";

    static final String EXTRA_UNIT_ID = "battly.rewarded.unit_id";
    static final String EXTRA_RECEIVER = "battly.rewarded.receiver";
    static final String EXTRA_ERROR = "battly.rewarded.error";
    static final int RESULT_REWARDED = 1;
    static final int RESULT_DISMISSED = 2;
    static final int RESULT_FAILED = 3;

    private final AtomicBoolean completed = new AtomicBoolean(false);
    private final AtomicBoolean rewardEarned = new AtomicBoolean(false);
    private ResultReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setFinishOnTouchOutside(false);
        showLoadingView();

        receiver = getIntent().getParcelableExtra(EXTRA_RECEIVER);
        String unitId = getIntent().getStringExtra(EXTRA_UNIT_ID);
        if (receiver == null || unitId == null || unitId.trim().isEmpty()) {
            complete(RESULT_FAILED, "Missing rewarded ad request data");
            return;
        }

        PojavApplication.sExecutorService.execute(() ->
                MobileAds.initialize(getApplicationContext(),
                        status -> runOnUiThread(() -> loadAd(unitId))));
    }

    private void showLoadingView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(190, 0, 0, 0));
        ProgressBar progress = new ProgressBar(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(progress, params);
        setContentView(root);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void loadAd(String unitId) {
        if (isFinishing() || isDestroyed()) return;
        RewardedInterstitialAd.load(
                this,
                unitId,
                new AdRequest.Builder().build(),
                new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(@NonNull RewardedInterstitialAd ad) {
                        Log.i(TAG, "Rewarded interstitial loaded in launcher process: "
                                + ad.getResponseInfo());
                        ad.setImmersiveMode(true);
                        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                complete(rewardEarned.get() ? RESULT_REWARDED : RESULT_DISMISSED, null);
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                                complete(RESULT_FAILED, formatError(
                                        adError.getCode(), adError.getDomain(), adError.getMessage(), null));
                            }
                        });
                        ad.show(BattlyRewardedInterstitialActivity.this,
                                reward -> rewardEarned.set(true));
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                        complete(RESULT_FAILED, formatError(
                                loadAdError.getCode(), loadAdError.getDomain(),
                                loadAdError.getMessage(), loadAdError.getResponseInfo()));
                    }
                });
    }

    private void complete(int resultCode, String error) {
        if (!completed.compareAndSet(false, true)) return;
        if (error != null) Log.e(TAG, "Rewarded interstitial failed: " + error);
        if (receiver != null) {
            Bundle result = new Bundle();
            if (error != null) result.putString(EXTRA_ERROR, error);
            receiver.send(resultCode, result);
        }
        finish();
    }

    private static String formatError(int code, String domain, String message, Object responseInfo) {
        StringBuilder detail = new StringBuilder()
                .append("code=").append(code)
                .append(", domain=").append(domain)
                .append(", message=").append(message);
        if (responseInfo != null) detail.append(", response=").append(responseInfo);
        return detail.toString();
    }
}
