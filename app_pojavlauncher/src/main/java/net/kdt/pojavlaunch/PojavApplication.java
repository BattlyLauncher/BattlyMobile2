package net.kdt.pojavlaunch;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import android.webkit.WebView;

import android.util.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.utils.*;
import net.kdt.pojavlaunch.utils.FileUtils;

public class PojavApplication extends Application {
	public static final String CRASH_REPORT_TAG = "PojavCrashReport";
	public static final ExecutorService sExecutorService = new ThreadPoolExecutor(4, 4, 500, TimeUnit.MILLISECONDS,  new LinkedBlockingQueue<>());
	private static PojavApplication sInstance;
	private boolean webViewDirectoryConfigured;

	public static Context getAppContext() {
		return sInstance;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		if (!webViewDirectoryConfigured) configureWebViewDataDirectory(this);
		sInstance = this;
		ContextExecutor.setApplication(this);
		Telemetry.initialize(this);
		Thread.setDefaultUncaughtExceptionHandler((thread, th) -> {
			Telemetry.recordLauncherCrash(thread.getName(), th);
			boolean storagePermAllowed = Tools.checkStorageRoot(PojavApplication.this);
			File crashFile = new File(storagePermAllowed ? Tools.DIR_GAME_HOME : Tools.DIR_DATA, "latestcrash.txt");
			try {
				// Write to file, since some devices may not able to show error
				FileUtils.ensureParentDirectory(crashFile);
				PrintStream crashStream = new PrintStream(crashFile);
				crashStream.append("Battly Launcher crash report\n");
				crashStream.append(" - Time: ").append(DateFormat.getDateTimeInstance().format(new Date())).append("\n");
				crashStream.append(" - Device: ").append(Build.PRODUCT).append(" ").append(Build.MODEL).append("\n");
				crashStream.append(" - Android version: ").append(Build.VERSION.RELEASE).append("\n");
				crashStream.append(" - Crash stack trace:\n");
				crashStream.append(" - Launcher version: " + BuildConfig.VERSION_NAME + "\n");
				crashStream.append(Log.getStackTraceString(th));
				crashStream.close();
			} catch (Throwable throwable) {
				Log.e(CRASH_REPORT_TAG, " - Exception attempt saving crash stack trace:", throwable);
				Log.e(CRASH_REPORT_TAG, " - The crash stack trace was:", th);
			}

			FatalErrorActivity.showError(PojavApplication.this, crashFile.getAbsolutePath(), storagePermAllowed, th);
			Tools.fullyExit();
		});
		
		try {
			if(Tools.checkStorageRoot(this)){
				// Implicitly initializes early constants and storage constants.
				// Required to run the main activity properly.
				LauncherPreferences.loadPreferences(this);
			} else {
				// In other cases, only initialize enough for the basicmost basics to work
				// and not explode.
				Tools.initEarlyConstants(this);
			}
			Architecture.initializeProcessArchitecture(getApplicationInfo().nativeLibraryDir);
			Tools.DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture();
			Log.i("BattlyArchitecture", "Process architecture: "
					+ Architecture.archAsString(Tools.DEVICE_ARCHITECTURE)
					+ " (nativeLibraryDir=" + getApplicationInfo().nativeLibraryDir + ")");
			// This migration reads and rewrites a control-layout JSON file. It is not
			// required to draw the first frame, so keep it off every Activity's startup.
			if (Tools.checkStorageRoot(this)) {
				sExecutorService.execute(BattlyControlLayouts::migrateDefaultPerformanceWidget);
				if (getPackageName().equals(currentProcessName(this))) {
					BattlyComponentUpdater.scheduleBackgroundCheck(this);
				}
			}
			//Force x86 lib directory for Asus x86 based zenfones
			if(Architecture.isx86Device() && Architecture.is32BitsDevice()){
				String originalJNIDirectory = getApplicationInfo().nativeLibraryDir;
				getApplicationInfo().nativeLibraryDir = originalJNIDirectory.substring(0,
												originalJNIDirectory.lastIndexOf("/"))
												.concat("/x86");
			}
		} catch (Throwable throwable) {
			Intent ferrorIntent = new Intent(this, FatalErrorActivity.class);
			ferrorIntent.putExtra("throwable", throwable);
			ferrorIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
			startActivity(ferrorIntent);
		}
	}

	private void configureWebViewDataDirectory(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
		String suffix = webViewSuffixForProcess(context.getPackageName(), currentProcessName(context));
		if (suffix == null) return;
		try {
			WebView.setDataDirectorySuffix(suffix);
			webViewDirectoryConfigured = true;
			Log.i("BattlyWebView", "Using isolated WebView directory: " + suffix);
		} catch (IllegalStateException exception) {
			Log.e("BattlyWebView", "WebView was initialized before process isolation", exception);
		}
	}

	private static String currentProcessName(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			return Application.getProcessName();
		}
		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (manager != null) {
			List<ActivityManager.RunningAppProcessInfo> processes = manager.getRunningAppProcesses();
			if (processes != null) {
				int pid = android.os.Process.myPid();
				for (ActivityManager.RunningAppProcessInfo process : processes) {
					if (process.pid == pid) return process.processName;
				}
			}
		}
		return context.getPackageName();
	}

	static String webViewSuffixForProcess(String packageName, String processName) {
		if (processName == null || processName.equals(packageName)) return null;
		int separator = processName.indexOf(':');
		String raw = separator >= 0 ? processName.substring(separator + 1) : processName;
		String suffix = raw.replaceAll("[^A-Za-z0-9_.-]", "_");
		return suffix.isEmpty() ? "secondary" : suffix;
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		ContextExecutor.clearApplication();
	}

	@Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
		configureWebViewDataDirectory(base);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        LocaleUtils.setLocale(this);
    }
}
