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
import androidx.collection.ArrayMap;
import androidx.constraintlayout.widget.ConstraintLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressService;

import java.util.ArrayList;
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
        final TextProgressBar textView;
        final LinearLayout.LayoutParams params;
        public LayoutProgressListener(String progressKey) {
            this.progressKey = progressKey;
            textView = new TextProgressBar(getContext());
            textView.setTextPadding(getContext().getResources().getDimensionPixelOffset(R.dimen._12sdp));
            params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, getResources().getDimensionPixelOffset(R.dimen._36sdp));
            params.bottomMargin = getResources().getDimensionPixelOffset(R.dimen._8sdp);
            ProgressKeeper.addListener(progressKey, this);
        }
        @Override
        public void onProgressStarted() {
            post(()-> {
                if (isProgressMuted(progressKey)) return;
                Log.i("ProgressLayout", "onProgressStarted");
                if (textView.getParent() == null) {
                    mLinearLayout.addView(textView, params);
                }
            });
        }

        @Override
        public void onProgressUpdated(int progress, int resid, Object... va) {
            post(()-> {
                if (isProgressMuted(progressKey)) {
                    if (textView.getParent() != null) {
                        mLinearLayout.removeView(textView);
                    }
                    return;
                }
                if (textView.getParent() == null) {
                    mLinearLayout.addView(textView, params);
                }
                textView.setProgress(progress);
                if(resid != -1) textView.setText(getContext().getString(resid, va));
                else if(va.length > 0 && va[0] != null)textView.setText((String)va[0]);
                else textView.setText("");
            });
        }

        @Override
        public void onProgressEnded() {
            post(()-> {
                if (textView.getParent() != null) {
                    mLinearLayout.removeView(textView);
                }
            });
        }
    }
}
