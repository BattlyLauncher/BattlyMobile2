package com.kdt.mcgui;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.R;

public final class BattlyProgressTaskView extends LinearLayout {
    private final ImageView mIcon;
    private final TextView mTitle;
    private final TextView mPercent;
    private final ProgressBar mProgress;

    public BattlyProgressTaskView(@NonNull Context context) {
        super(context);
        LayoutInflater.from(context).inflate(R.layout.view_battly_progress_task, this, true);
        mIcon = findViewById(R.id.progress_task_icon);
        mTitle = findViewById(R.id.progress_task_title);
        mPercent = findViewById(R.id.progress_task_percent);
        mProgress = findViewById(R.id.progress_task_bar);
    }

    public void setTaskIcon(@DrawableRes int icon) {
        mIcon.setImageResource(icon);
    }

    public void update(int progress, String title) {
        mTitle.setText(title);
        boolean determinate = progress >= 0;
        mProgress.setIndeterminate(!determinate);
        if (determinate) {
            int boundedProgress = Math.max(0, Math.min(100, progress));
            mProgress.setProgress(boundedProgress);
            mPercent.setText(boundedProgress + "%");
        } else {
            mPercent.setText(R.string.global_waiting);
        }
    }
}
