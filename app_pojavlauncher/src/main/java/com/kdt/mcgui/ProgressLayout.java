package com.kdt.mcgui;


import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressService;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.HashSet;
import java.util.Set;


/** Class staring at specific values and automatically show something if the progress is present
 * Since progress is posted in a specific way, The packing/unpacking is handheld by the class
 *
 * This class relies on ExtraCore for its behavior.
 */
public class ProgressLayout extends ConstraintLayout implements View.OnClickListener, TaskCountListener{
    public static final String UNPACK_RUNTIME = "unpack_runtime";
    public static final String DOWNLOAD_MINECRAFT = "download_minecraft";
    public static final String DOWNLOAD_VERSION_LIST = "download_verlist";
    public static final String AUTHENTICATE_MICROSOFT = "authenticate_microsoft";
    public static final String INSTALL_MODPACK = "install_modpack";
    public static final String EXTRACT_COMPONENTS = "extract_components";
    public static final String EXTRACT_SINGLE_FILES = "extract_single_files";
    private static final Set<String> sMutedProgressKeys = new HashSet<>();

    public ProgressLayout(@NonNull Context context) {
        super(context);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    public ProgressLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private final ArrayList<LayoutProgressListener> mMap = new ArrayList<>();
    private LinearLayout mLinearLayout;
    private TextView mTaskNumberDisplayer;
    private TextView mTaskBadge;
    private ImageView mFlipArrow;



    public void observe(String progressKey){
        mMap.add(new LayoutProgressListener(progressKey));
    }

    public static synchronized void setProgressMuted(String progressKey, boolean muted) {
        if (muted) {
            sMutedProgressKeys.add(progressKey);
        } else {
            sMutedProgressKeys.remove(progressKey);
        }
    }

    private static synchronized boolean isProgressMuted(String progressKey) {
        return sMutedProgressKeys.contains(progressKey);
    }

    public void cleanUpObservers() {
        for(LayoutProgressListener progressListener : mMap) {
            ProgressKeeper.removeListener(progressListener.progressKey, progressListener);
        }
    }

    public boolean hasProcesses(){
        return ProgressKeeper.getTaskCount() > 0;
    }


    private void init(){
        inflate(getContext(), R.layout.view_progress, this);
        mLinearLayout = findViewById(R.id.progress_linear_layout);
        mTaskNumberDisplayer = findViewById(R.id.progress_textview);
        mTaskBadge = findViewById(R.id.progress_task_badge);
        mFlipArrow = findViewById(R.id.progress_flip_arrow);
        setOnClickListener(this);
    }


    /** Update the progress bar content */
    public static void setProgress(String progressKey, int progress){
        ProgressKeeper.submitProgress(progressKey, progress, -1, (Object)null);
    }

    /** Update the text and progress content */
    public static void setProgress(String progressKey, int progress, @StringRes int resource, Object... message){
        ProgressKeeper.submitProgress(progressKey, progress, resource, message);
    }

    /** Update the text and progress content */
    public static void setProgress(String progressKey, int progress, String message){
        setProgress(progressKey,progress, -1, message);
    }

    /** Update the text and progress content */
    public static void clearProgress(String progressKey){
        setProgress(progressKey, -1, -1);
    }

    @Override
    public void onClick(View v) {
        setExpanded(mLinearLayout.getVisibility() == GONE);
    }

    public void setExpanded(boolean expand) {
        mLinearLayout.setVisibility(expand ? VISIBLE : GONE);
        mFlipArrow.animate().rotation(expand ? 180f : 0f).setDuration(160).start();
    }

    @Override
    public void onUpdateTaskCount(int tc) {
        post(()->{
            int visibleTasks = Math.max(0, tc - sMutedProgressKeys.size());
            if(visibleTasks > 0) {
                mTaskNumberDisplayer.setText(getContext().getString(R.string.progresslayout_tasks_in_progress, visibleTasks));
                mTaskBadge.setText(String.valueOf(visibleTasks));
                setVisibility(VISIBLE);
            }else {
                mTaskBadge.setText("0");
                mLinearLayout.setVisibility(GONE);
                mFlipArrow.setRotation(0f);
                setVisibility(GONE);
            }
        });
    }

    class LayoutProgressListener implements ProgressListener {
        final String progressKey;
        final BattlyProgressTaskView taskView;
        final LinearLayout.LayoutParams params;
        public LayoutProgressListener(String progressKey) {
            this.progressKey = progressKey;
            taskView = new BattlyProgressTaskView(getContext());
            taskView.setTaskIcon(iconForProgress(progressKey));
            taskView.update(-1, getContext().getString(R.string.global_waiting));
            params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.bottomMargin = getResources().getDimensionPixelOffset(R.dimen._8sdp);
            ProgressKeeper.addListener(progressKey, this);
        }
        @Override
        public void onProgressStarted() {
            post(()-> {
                if (isProgressMuted(progressKey)) return;
                Log.i("ProgressLayout", "onProgressStarted");
                if (taskView.getParent() == null) {
                    mLinearLayout.addView(taskView, params);
                }
            });
        }

        @Override
        public void onProgressUpdated(int progress, int resid, Object... va) {
            post(()-> {
                if (isProgressMuted(progressKey)) {
                    if (taskView.getParent() != null) {
                        mLinearLayout.removeView(taskView);
                    }
                    return;
                }
                if (taskView.getParent() == null) {
                    mLinearLayout.addView(taskView, params);
                }
                String status;
                if(resid != -1) status = formatProgressText(resid, va);
                else if(va != null && va.length > 0 && va[0] != null) status = String.valueOf(va[0]);
                else status = getContext().getString(R.string.global_waiting);
                taskView.update(progress, status);
            });
        }

        private String formatProgressText(@StringRes int resid, Object... args) {
            try {
                return getContext().getString(resid, args);
            } catch (IllegalFormatException exception) {
                Log.e("ProgressLayout", "Invalid progress format for resource " + resid, exception);
                return getContext().getString(resid);
            }
        }

        @Override
        public void onProgressEnded() {
            post(()-> {
                if (taskView.getParent() != null) {
                    mLinearLayout.removeView(taskView);
                }
            });
        }
    }

    private static int iconForProgress(String progressKey) {
        if (UNPACK_RUNTIME.equals(progressKey)) {
            return R.drawable.ic_setting_java_runtime;
        }
        if (AUTHENTICATE_MICROSOFT.equals(progressKey)) {
            return R.drawable.ic_ms_logo;
        }
        return R.drawable.ic_menu_install_jar;
    }
}
