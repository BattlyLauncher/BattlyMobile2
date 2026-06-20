package net.kdt.pojavlaunch.modloaders;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.lifecycle.ContextExecutorTask;

import java.io.File;
import java.io.IOException;

public class DetachedModloaderDownloadListener implements ModloaderDownloadListener {
    private final Context mContext;
    private final String mNoDataMessage;
    @Nullable
    private final String mSuccessLabel;

    public DetachedModloaderDownloadListener(Context context, CharSequence noDataMessage, @Nullable String successLabel) {
        mContext = context.getApplicationContext();
        mNoDataMessage = String.valueOf(noDataMessage);
        mSuccessLabel = successLabel;
    }

    @Override
    public void onDownloadFinished(File downloadedFile) {
        ContextExecutor.execute(new ContextExecutorTask() {
            @Override
            public void executeWithActivity(Activity activity) {
                if (activity instanceof LauncherActivity) {
                    ((LauncherActivity) activity).refreshHomeProfileUi();
                }
                if (mSuccessLabel != null) {
                    Toast.makeText(activity, activity.getString(R.string.installer_internal_success, mSuccessLabel), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void executeWithApplication(Context context) {
                if (mSuccessLabel != null) {
                    Toast.makeText(context, context.getString(R.string.installer_internal_success, mSuccessLabel), Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onDataNotAvailable() {
        IOException exception = new IOException(mNoDataMessage);
        ContextExecutor.execute(new ContextExecutorTask() {
            @Override
            public void executeWithActivity(Activity activity) {
                if (activity instanceof LauncherActivity) {
                    ((LauncherActivity) activity).refreshHomeProfileUi();
                }
                Tools.dialog(activity, activity.getString(R.string.global_error), mNoDataMessage);
            }

            @Override
            public void executeWithApplication(Context context) {
                Tools.showErrorRemote(mNoDataMessage, exception);
            }
        });
    }

    @Override
    public void onDownloadError(Exception e) {
        ContextExecutor.execute(new ContextExecutorTask() {
            @Override
            public void executeWithActivity(Activity activity) {
                if (activity instanceof LauncherActivity) {
                    ((LauncherActivity) activity).refreshHomeProfileUi();
                }
                Tools.showError(activity, e);
            }

            @Override
            public void executeWithApplication(Context context) {
                Tools.showErrorRemote(context, R.string.modpack_install_modloader_download_failed, e);
            }
        });
    }
}
