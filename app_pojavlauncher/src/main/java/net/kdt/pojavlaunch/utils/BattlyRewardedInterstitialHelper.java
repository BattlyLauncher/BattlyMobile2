package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.util.Log;

public final class BattlyRewardedInterstitialHelper {
    private static final String TAG = "BattlyRewardedAd";

    public interface Callback {
        void onRewardEarned();
        void onDismissedWithoutReward();
        void onFailed(String message);
    }

    private BattlyRewardedInterstitialHelper() {
    }

    public static void loadAndShow(Activity activity, String unitId, Callback callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            callback.onFailed("Activity is not available");
            return;
        }
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                switch (resultCode) {
                    case BattlyRewardedInterstitialActivity.RESULT_REWARDED:
                        callback.onRewardEarned();
                        break;
                    case BattlyRewardedInterstitialActivity.RESULT_DISMISSED:
                        callback.onDismissedWithoutReward();
                        break;
                    default:
                        String detail = resultData == null
                                ? "Unknown rewarded ad error"
                                : resultData.getString(BattlyRewardedInterstitialActivity.EXTRA_ERROR,
                                        "Unknown rewarded ad error");
                        Log.e(TAG, "Rewarded interstitial bridge failed: " + detail);
                        callback.onFailed(detail);
                        break;
                }
            }
        };

        Intent intent = new Intent(activity, BattlyRewardedInterstitialActivity.class)
                .putExtra(BattlyRewardedInterstitialActivity.EXTRA_UNIT_ID, unitId)
                .putExtra(BattlyRewardedInterstitialActivity.EXTRA_RECEIVER, receiver);
        try {
            activity.startActivity(intent);
        } catch (RuntimeException exception) {
            Log.e(TAG, "Could not start rewarded interstitial bridge", exception);
            callback.onFailed(exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage());
        }
    }
}
