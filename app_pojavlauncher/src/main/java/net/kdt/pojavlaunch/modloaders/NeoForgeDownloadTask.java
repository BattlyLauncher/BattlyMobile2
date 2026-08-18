package net.kdt.pojavlaunch.modloaders;

import android.content.Context;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.fragments.NeoForgeInstallFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class NeoForgeDownloadTask implements Runnable, Tools.DownloaderFeedback {
    private final String mDownloadUrl;
    private final String mLoaderVersion;

    private final Context mContext;
    private final ModloaderDownloadListener mListener;
    private final boolean mCreateProfile;

    public NeoForgeDownloadTask(Context context, ModloaderDownloadListener listener, @NonNull String loaderVersion) {
        this(context, listener, loaderVersion, true);
    }

    public NeoForgeDownloadTask(Context context, ModloaderDownloadListener listener, @NonNull String loaderVersion, boolean createProfile) {
        this.mContext = context.getApplicationContext();
        this.mListener = listener;
        this.mDownloadUrl = String.format(NEOFORGE_INSTALLER_URL, loaderVersion);
        this.mLoaderVersion = loaderVersion;
        this.mCreateProfile = createProfile;
    }

    private static final String NEOFORGE_INSTALLER_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/%1$s/neoforge-%1$s-installer.jar";

    @Override
    public void run() {
        try {
            if(determineDownloadUrl()) {
                downloadNeoForge();
            }
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }

    @Override
    public void updateProgress(int curr, int max) {
        int progress100 = max > 0 ? (int)(((float)curr / (float)max)*100f) : 0;
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.forge_dl_progress, mLoaderVersion);
    }

    private void downloadNeoForge() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.forge_dl_progress, mLoaderVersion);
        try {
            File destinationFile = downloadVerifiedInstaller();
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 95, R.string.modloader_installing);
            NeoForgeInstaller.install(mContext, destinationFile, mLoaderVersion, mCreateProfile);
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 100, R.string.modloader_installing);
            mListener.onDownloadFinished(null);
        }catch (FileNotFoundException e) {
            mListener.onDataNotAvailable();
        } catch (IOException e) {
            mListener.onDownloadError(e);
        } catch (RuntimeException e) {
            mListener.onDownloadError(e);
        }
    }

    private File downloadVerifiedInstaller() throws IOException {
        String safeVersion = mLoaderVersion.replaceAll("[^a-zA-Z0-9._-]", "_");
        File destinationFile = new File(Tools.DIR_CACHE,
                "neoforge-" + safeVersion + "-installer.jar");
        IOException lastError = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            if (destinationFile.exists() && !destinationFile.delete()) {
                throw new IOException("Unable to clear the previous NeoForge installer download");
            }
            try {
                byte[] buffer = new byte[65535];
                DownloadUtils.downloadFileMonitored(mDownloadUrl, destinationFile, buffer, this);
                ModloaderInstallUtils.validateInstallerJar(destinationFile);
                return destinationFile;
            } catch (IOException exception) {
                lastError = exception;
                if (destinationFile.exists() && !destinationFile.delete()) {
                    throw new IOException("NeoForge installer is corrupt and could not be removed",
                            exception);
                }
            }
        }
        throw new IOException("NeoForge installer download was corrupt after 3 attempts", lastError);
    }

    public boolean determineDownloadUrl() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.neoforge_dl_searching);
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
        List<String> neoforgeVersions = NeoForgeInstallFragment.downloadNeoForgeVersions();
        if(neoforgeVersions == null) return false;
        for(String versionName : neoforgeVersions) {
            if(!versionName.startsWith(mLoaderVersion)) continue;
            return true;
        }
        return false;
    }

}
