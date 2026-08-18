package net.kdt.pojavlaunch.customcontrols.buttons;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlLayout;
import net.kdt.pojavlaunch.customcontrols.PerformanceHudStats;
import net.kdt.pojavlaunch.customcontrols.handleview.EditControlSideDialog;

@SuppressLint("ViewConstructor")
public final class ControlPerformanceWidget extends ControlButton {
    private static final long REFRESH_INTERVAL_MS = 500;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatsText();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public ControlPerformanceWidget(ControlLayout layout, ControlData properties) {
        super(layout, properties);
        setAllCaps(false);
        setTextSize(12);
        setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        setSingleLine(true);
        setContentDescription(getContext().getString(R.string.customctrl_performance_widget));
        updateStatsText();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PerformanceHudStats.acquire();
        refreshHandler.post(refreshRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        refreshHandler.removeCallbacks(refreshRunnable);
        PerformanceHudStats.release();
        super.onDetachedFromWindow();
    }

    @Override
    public void sendKeyPresses(boolean isDown) {
        // This control displays data only.
    }

    @Override
    public boolean triggerToggle() {
        return false;
    }

    @Override
    public void loadEditValues(EditControlSideDialog editControlPopup) {
        editControlPopup.loadPerformanceValues(getProperties());
    }

    private void updateStatsText() {
        int fps = PerformanceHudStats.getFps();
        int ping = PerformanceHudStats.getPingMs();
        String fpsText = fps > 0 ? Integer.toString(fps) : "--";
        if (ping >= 0) {
            setText(getContext().getString(
                    R.string.customctrl_performance_value,
                    fpsText,
                    Integer.toString(ping)));
        } else {
            setText(getContext().getString(R.string.customctrl_performance_fps_only, fpsText));
        }
    }
}
