package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class ForgeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private String mDownloadUrl;
    private String mFullVersion;
    private String mLoaderVersion;
    private String mGameVersion;
    private final Context mContext;
    private final ModloaderDownloadListener mListener;
    private final boolean mCreateProfile;

    public ForgeDownloadTask(Context context, ModloaderDownloadListener listener, String forgeVersion) {
        this(context, listener, forgeVersion, true);
    }

    public ForgeDownloadTask(Context context, ModloaderDownloadListener listener, String forgeVersion, boolean createProfile) {
        this.mContext = context.getApplicationContext();
        this.mListener = listener;
        this.mDownloadUrl = ForgeUtils.getInstallerUrl(forgeVersion);
        this.mFullVersion = forgeVersion;
        this.mCreateProfile = createProfile;
    }

    public ForgeDownloadTask(Context context, ModloaderDownloadListener listener, String gameVersion, String loaderVersion) {
        this(context, listener, gameVersion, loaderVersion, false);
    }

    public ForgeDownloadTask(Context context, ModloaderDownloadListener listener, String gameVersion, String loaderVersion, boolean createProfile) {
        this.mContext = context.getApplicationContext();
        this.mListener = listener;
        this.mLoaderVersion = loaderVersion;
        this.mGameVersion = gameVersion;
        this.mCreateProfile = createProfile;
    }
    @Override
    public void run() {
        if(determineDownloadUrl()) {
            downloadForge();
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.forge_dl_progress, mFullVersion);
    }

    private void downloadForge() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_progress, mFullVersion);
        try {
            File destinationFile = new File(Tools.DIR_CACHE, "forge-installer.jar");
            byte[] buffer = new byte[8192];
            DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 95, R.string.modloader_installing);
            ForgeInstaller.install(mContext, destinationFile, mCreateProfile);
            mListener.onDownloadFinished(null);
        }catch (FileNotFoundException e) {
            mListener.onDataNotAvailable();
        } catch (IOException e) {
            mListener.onDownloadError(e);
        }
    }

    public boolean determineDownloadUrl() {
        if(mDownloadUrl != null && mFullVersion != null) return true;
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_searching);
        try {
            if(!findVersion()) {
                mListener.onDataNotAvailable();
                return false;
            }
        }catch (IOException e) {
            mListener.onDownloadError(e);
            return false;
        }
        return true;
    }

    public boolean findVersion() throws IOException {
        List<String> forgeVersions = ForgeUtils.downloadForgeVersions();
        if(forgeVersions == null) return false;
        String versionStart = mGameVersion+"-"+mLoaderVersion;
        for(String versionName : forgeVersions) {
            if(!versionName.startsWith(versionStart)) continue;
            mFullVersion = versionName;
            mDownloadUrl = ForgeUtils.getInstallerUrl(mFullVersion);
            return true;
        }
        return false;
    }

}
