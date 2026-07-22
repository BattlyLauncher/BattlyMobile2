package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.battlysocial.BattlySocialManager;
import net.kdt.pojavlaunch.battlysocial.BattlySocialNotifications;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.*;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.ModloaderInstallTracker;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener;
import net.kdt.pojavlaunch.modloaders.SmartJarInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceControlFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceExperimentalFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceJavaFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceMiscellaneousFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceRendererSettingsFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.BattlyBackgrounds;
import net.kdt.pojavlaunch.utils.BattlyInAppMessaging;
import net.kdt.pojavlaunch.utils.BattlyPlusCloud;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;
import net.kdt.pojavlaunch.utils.BattlyPlusWelcomeDialog;
import net.kdt.pojavlaunch.utils.BattlyUpdateVideoDialog;
import net.kdt.pojavlaunch.utils.DateUtils;
import net.kdt.pojavlaunch.utils.JavaRuntimeInstallDialog;
import net.kdt.pojavlaunch.utils.CrashAnalysisEngine;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import net.kdt.pojavlaunch.utils.NotificationUtils;
import net.kdt.pojavlaunch.onboarding.OnboardingActivity;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Locale;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";
    public static final String EXTRA_GAME_EXIT_CODE = "net.kdt.pojavlaunch.extra.GAME_EXIT_CODE";
    public static final String EXTRA_GAME_EXIT_DETAILS = "net.kdt.pojavlaunch.extra.GAME_EXIT_DETAILS";
    private static final String GAME_SESSION_PREFS = "battly_game_session";
    private static final String GAME_SESSION_ACTIVE = "active";
    private static final String GAME_SESSION_VERSION = "version";
    private static final String GAME_SESSION_LOG_OFFSET = "log_offset";

    public final ActivityResultLauncher<Object> modInstallerLauncher = registerForActivityResult(
            new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null)
                    SmartJarInstaller.install(this, data);
            });
    public final ActivityResultLauncher<Object> modpackImportLauncher = registerForActivityResult(
            new OpenDocumentWithExtension(new String[] { "zip", "mrpack" }), (data) -> {
                if (data != null) {
                    PojavApplication.sExecutorService.execute(() -> {
                        try {
                            ModLoader loaderInfo = new CommonApi(getString(R.string.curseforge_api_key))
                                    .importModpack(this, data);
                            if (loaderInfo == null)
                                return;
                            loaderInfo.getDownloadTask(this, new NotificationDownloadListener(this, loaderInfo)).run();
                        } catch (IOException e) {
                            Tools.showErrorRemote(this, R.string.modpack_install_download_failed, e);
                        } catch (IllegalArgumentException e) {
                            Tools.showError(this, R.string.not_modpack_file, e);
                        } catch (NoSuchAlgorithmException e) {
                            // Should literally never happen because SHA-1 is required Java spec
                            throw new RuntimeException(e);
                        }
                    });
                }
            });

    private mcAccountSpinner mAccountSpinner;
    private FragmentContainerView mFragmentView;
    private ImageView mLauncherBackground;
    private ImageView mLauncherBackgroundNext;
    private VideoView mLauncherBackgroundVideo;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private View mLauncherHeader;
    private View mLauncherOverlay;
    private View mLauncherScrim;
    private View mBrandBlock;
    private View mTopActions;
    private View mPanelNavigation;
    private ImageButton mBackButton;
    private ImageButton mHomeButton;
    private TextView mPanelTitle;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ModloaderInstallTracker mInstallTracker;
    private NotificationManager mNotificationManager;
    private final Handler mBackgroundAnimationHandler = new Handler(Looper.getMainLooper());
    private Runnable mBackgroundAnimationRunnable;
    private int mAnimatedBackgroundIndex;
    private boolean mStartupPromptChainStarted;
    private boolean mLauncherStartupInitialized;
    private boolean mGameExitInfoShown;
    private Runnable mAfterWhatsNew;
    private final ActivityResultLauncher<Intent> mWhatsNewLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Runnable after = mAfterWhatsNew;
                mAfterWhatsNew = null;
                if (after != null) after.run();
            });
    private BroadcastReceiver mBattlyInAppReceiver;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            updateLauncherChrome(f);
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if (value.equals("true"))
            onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to
        // fragment itself ?
        if (!(fragment instanceof MainMenuFragment))
            return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        Fragment fragment = getSupportFragmentManager().findFragmentById(mFragmentView.getId());
        if (fragment instanceof MainMenuFragment) {
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else {
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if (mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            resetLaunchGameUi();
            return false;
        }

        String selectedProfile = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        if (LauncherProfiles.mainProfileJson == null
                || !LauncherProfiles.mainProfileJson.profiles.containsKey(selectedProfile)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            resetLaunchGameUi();
            return false;
        }
        MinecraftProfile prof = LauncherProfiles.mainProfileJson.profiles.get(selectedProfile);
        if (prof == null || prof.lastVersionId == null || "Unknown".equals(prof.lastVersionId)) {
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            resetLaunchGameUi();
            return false;
        }

        if (mAccountSpinner.getSelectedAccount() == null) {
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            resetLaunchGameUi();
            return false;
        }
        if (!JavaRuntimeInstallDialog.isJava8Ready()) {
            resetLaunchGameUi();
            JavaRuntimeInstallDialog.ensureJava8(this,
                    () -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
            return false;
        }
        String normalizedVersionId = AsyncMinecraftDownloader.normalizeVersionId(prof.lastVersionId);
        JMinecraftVersionList.Version mcVersion = AsyncMinecraftDownloader.getListedVersion(normalizedVersionId);
        Telemetry.logLaunchRequested(selectedProfile, normalizedVersionId);

        // Do not load when is a modded version or older than minecraft 1.3 on demo
        // account
        if (mAccountSpinner.getSelectedAccount().isDemo()) {
            boolean isOlderThan13 = true;

            if (mcVersion != null) {
                try {
                    isOlderThan13 = DateUtils.dateBefore(DateUtils.parseReleaseDate(mcVersion.releaseTime), 2012, 6,
                            22);
                } catch (ParseException ignored) {
                }
            }

            if (isOlderThan13) {
                hasNoOnlineProfileDialog(this, getString(R.string.global_error),
                        getString(R.string.demo_versions_supported));
                resetLaunchGameUi();
                return false;
            }
        }

        new MinecraftDownloader().start(
                this,
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId));
        return false;
    };

    private void resetLaunchGameUi() {
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME_UI_RESET, true);
    }

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if (taskCount > 0) {
            Tools.runOnUiThread(() -> mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START));
        }
    };

    private ActivityResultLauncher<String> mRequestNotificationPermissionLauncher;
    private ActivityResultLauncher<String> mRequestMicrophonePermissionLauncher;
    private WeakReference<Runnable> mRequestNotificationPermissionRunnable;
    private WeakReference<Runnable> mRequestMicrophonePermissionRunnable;

    @Override
    protected boolean shouldIgnoreNotch() {
        return true;
    }

    @Override
    public boolean setFullscreen() {
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        handleBattlyOAuthIntent(getIntent());
        FragmentManager fragmentManager = getSupportFragmentManager();
        // If we don't have a back stack root yet...
        if (fragmentManager.getBackStackEntryCount() < 1) {
            // Manually add the first fragment to the backstack to get easily back to it
            // There must be a better way to handle the root though...
            // (artDev: No, there is not. I've spent days researching this for another
            // unrelated project.)
            fragmentManager.beginTransaction()
                    .setReorderingAllowed(true)
                    .addToBackStack("ROOT")
                    .add(R.id.container_fragment, MainMenuFragment.class, null, "ROOT").commit();
        }

        IconCacheJanitor.runJanitor();
        mRequestNotificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed)
                        handleNoNotificationPermission();
                    Runnable runnable = Tools.getWeakReference(mRequestNotificationPermissionRunnable);
                    if (runnable != null)
                        runnable.run();
                });
        mRequestMicrophonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if (!isAllowed)
                        handleNoNotificationPermission();
                    else {
                        Runnable runnable = Tools.getWeakReference(mRequestMicrophonePermissionRunnable);
                        if (runnable != null)
                            runnable.run();
                    }
                });
        getWindow().setBackgroundDrawable(null);
        bindViews();
        registerBattlyInAppReceiver();

        BattlyUpdateVideoDialog.showIfNeeded(this, this::continueLauncherStartupAfterTrailer);
    }

    private void continueLauncherStartupAfterTrailer() {
        if (mLauncherStartupInitialized || isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        mLauncherStartupInitialized = true;

        if (!LauncherPreferences.PREF_BATTLY_ONBOARDING_COMPLETED) {
            Intent intent = new Intent(this, net.kdt.pojavlaunch.onboarding.OnboardingActivity.class);
            try {
                startActivity(intent);
                finish();
            } catch (IllegalStateException e) {
                Log.w("LauncherActivity", "Ignoring onboarding launch after state was saved", e);
            }
            return;
        }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        mSettingsButton.setOnClickListener(mSettingButtonListener);
        mBackButton.setOnClickListener(v -> onBackPressed());
        mHomeButton.setOnClickListener(v -> Tools.backToMainMenu(this));
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        // Force login screen if no accounts are configured.
        // executePendingTransactions() ensures MainMenuFragment is committed
        // before the listener fires, so findFragmentById() returns a valid fragment.
        if (getSupportFragmentManager().isStateSaved()) {
            Log.w("LauncherActivity", "Startup skipped because FragmentManager state is already saved");
            return;
        }
        getSupportFragmentManager().executePendingTransactions();
        if (PojavProfile.getAllProfilesList().isEmpty()) {
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
        }

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions),
                false);

        mInstallTracker = new ModloaderInstallTracker(this);

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_MINECRAFT);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE_MICROSOFT);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        BattlyWorldsInvites.handleLauncherIntent(this, getIntent());
        handleBattlySocialIntent(getIntent());
        handleSharedInstallationIntent(getIntent());
        showGameExitInfoIfNeeded(getIntent());
        BattlyInAppMessaging.showPendingIfAny(this);
        maybeRunStartupPrompts();
    }

    private void showUpdateVideoWhenReady() {
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> BattlyUpdateVideoDialog.showIfNeeded(this,
                        () -> showWhatsNewIfNeeded(
                                () -> checkNotificationPermission(this::ensureJavaRuntimeAfterStartup))), 900);
    }

    private void showWhatsNewIfNeeded(Runnable afterDone) {
        if (LauncherPreferences.DEFAULT_PREF.getBoolean(OnboardingActivity.PREF_WHATS_NEW_SEEN, false)) {
            if (afterDone != null) afterDone.run();
            return;
        }
        mAfterWhatsNew = afterDone;
        Intent intent = new Intent(this, OnboardingActivity.class)
                .putExtra(OnboardingActivity.EXTRA_WHATS_NEW, true);
        mWhatsNewLauncher.launch(intent);
    }

    private void ensureJavaRuntimeAfterStartup() {
        JavaRuntimeInstallDialog.ensureJava8(this, null);
    }

    public void runStartupPromptsAfterLogin() {
        mStartupPromptChainStarted = false;
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> runStartupPromptChain(false, true),
                650
        );
    }

    public void maybeRunStartupPrompts() {
        runStartupPromptChain(true, false);
    }

    private void runStartupPromptChain(boolean requireAccount, boolean forcePlusWelcome) {
        if (mStartupPromptChainStarted || (requireAccount && PojavProfile.getAllProfilesList().isEmpty())) {
            return;
        }
        mStartupPromptChainStarted = true;
        BattlyPlusManager.refreshAsync(this, plus -> {
            Runnable afterPlus = this::showUpdateVideoWhenReady;
            if (forcePlusWelcome) {
                BattlyPlusWelcomeDialog.showAfterLogin(this, afterPlus);
            } else {
                BattlyPlusWelcomeDialog.showIfNeeded(this, afterPlus);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleBattlyOAuthIntent(intent);
        BattlyWorldsInvites.handleLauncherIntent(this, intent);
        handleBattlySocialIntent(intent);
        handleSharedInstallationIntent(intent);
        showGameExitInfoIfNeeded(intent);
    }

    private void handleBattlyOAuthIntent(Intent intent) {
        if (intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri uri = intent.getData();
        if (!"battlymobile".equalsIgnoreCase(uri.getScheme()) || !"oauth".equalsIgnoreCase(uri.getHost())) {
            return;
        }
        String provider = uri.getQueryParameter("provider");
        String token = uri.getQueryParameter("token");
        String nonce = uri.getQueryParameter("nonce");
        if (!Tools.isValidString(provider) || !Tools.isValidString(token)) {
            return;
        }
        getSharedPreferences("battly_oauth", MODE_PRIVATE)
                .edit()
                .putString("provider", provider)
                .putString("token", token)
                .putString("nonce", nonce == null ? "" : nonce)
                .apply();
        intent.setData(null);
        Toast.makeText(this, R.string.battly_login_completing, Toast.LENGTH_SHORT).show();
    }

    private void handleBattlySocialIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(BattlySocialNotifications.EXTRA_OPEN_SOCIAL, false)) {
            return;
        }
        intent.removeExtra(BattlySocialNotifications.EXTRA_OPEN_SOCIAL);
        Tools.MAIN_HANDLER.post(() -> {
            if (isFinishing() || getSupportFragmentManager().isStateSaved()) return;
            Tools.swapFragment(this, BattlySocialFragment.class, BattlySocialFragment.TAG, null);
        });
    }

    private void handleSharedInstallationIntent(Intent intent) {
        if (intent == null || intent.getData() == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri uri = intent.getData();
        String code = null;
        if ("battly".equalsIgnoreCase(uri.getScheme()) && "install".equalsIgnoreCase(uri.getHost())) {
            code = uri.getLastPathSegment();
        } else if ("https".equalsIgnoreCase(uri.getScheme())
                && "battlylauncher.com".equalsIgnoreCase(uri.getHost())
                && uri.getPath() != null
                && uri.getPath().startsWith("/mobile/install/")) {
            code = uri.getLastPathSegment();
        }
        if (code == null || code.trim().isEmpty()) {
            return;
        }
        intent.setData(null);
        String finalCode = code;
        BattlyPlusCloud.importSharedInstallation(this, finalCode, (ok, message) ->
                Toast.makeText(this, message, ok ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
    }

    public static void openAfterGameExit(Context context, int exitCode, String details) {
        Intent intent = new Intent(context, LauncherActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_GAME_EXIT_CODE, exitCode);
        if (details != null && !details.trim().isEmpty()) {
            intent.putExtra(EXTRA_GAME_EXIT_DETAILS, details.trim());
        }
        context.startActivity(intent);
    }

    public static void markGameSessionStarting(Context context, String versionId) {
        File latestLog = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        context.getSharedPreferences(GAME_SESSION_PREFS, MODE_PRIVATE).edit()
                .putBoolean(GAME_SESSION_ACTIVE, true)
                .putString(GAME_SESSION_VERSION, versionId == null ? "" : versionId)
                .putLong(GAME_SESSION_LOG_OFFSET, latestLog.isFile() ? latestLog.length() : 0L)
                .apply();
    }

    private void inspectReturnedGameSession() {
        android.content.SharedPreferences session = getSharedPreferences(GAME_SESSION_PREFS, MODE_PRIVATE);
        if (!session.getBoolean(GAME_SESSION_ACTIVE, false)) return;
        String version = session.getString(GAME_SESSION_VERSION, "");
        long logOffset = session.getLong(GAME_SESSION_LOG_OFFSET, 0L);
        session.edit().clear().apply();

        File latestLog = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if (!latestLog.isFile()) return;
        try {
            String log = readGameSessionLog(latestLog, logOffset);
            String lower = log.toLowerCase(Locale.ROOT);
            boolean crashed = lower.contains("game crashed!")
                    || lower.contains("---- minecraft crash report ----")
                    || lower.contains("exception in thread \"main\"")
                    || lower.contains("unable to launch")
                    || lower.contains("unsatisfiedlinkerror")
                    || lower.contains("fatal exception")
                    || lower.contains("error during pre-loading phase")
                    || lower.contains("modloadingexception")
                    || lower.contains("needs language provider javafml");
            boolean cleanExit = !crashed && (lower.contains("stopping!")
                    || lower.contains("java exit code: 0"));
            if (crashed || !cleanExit && lower.contains("java exit code:")) {
                mGameExitInfoShown = false;
                Intent crashIntent = new Intent()
                        .putExtra(EXTRA_GAME_EXIT_CODE, -1)
                        .putExtra(EXTRA_GAME_EXIT_DETAILS,
                                "Minecraft " + version + " ended unexpectedly.");
                showGameExitInfoIfNeeded(crashIntent);
            }
        } catch (IOException exception) {
            Log.w("LauncherActivity", "Unable to inspect the previous game session", exception);
        }
    }

    private static String readGameSessionLog(File file, long requestedOffset) throws IOException {
        long length = file.length();
        long offset = requestedOffset >= 0 && requestedOffset <= length ? requestedOffset : 0L;
        long available = length - offset;
        if (available > 256 * 1024L) offset = length - 256 * 1024L;
        byte[] bytes = new byte[(int) (length - offset)];
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(offset);
            input.readFully(bytes);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void showGameExitInfoIfNeeded(Intent intent) {
        if (mGameExitInfoShown || intent == null || !intent.hasExtra(EXTRA_GAME_EXIT_CODE)) {
            return;
        }
        int exitCode = intent.getIntExtra(EXTRA_GAME_EXIT_CODE, 0);
        String details = intent.getStringExtra(EXTRA_GAME_EXIT_DETAILS);
        intent.removeExtra(EXTRA_GAME_EXIT_CODE);
        intent.removeExtra(EXTRA_GAME_EXIT_DETAILS);
        if (exitCode == 0 && (details == null || details.trim().isEmpty())) {
            return;
        }
        mGameExitInfoShown = true;
        String message = getString(R.string.minecraft_crash_message, exitCode);
        if (details != null && !details.trim().isEmpty()) {
            message += "\n\n" + details.trim();
        }
        MinecraftProfile crashProfile = LauncherProfiles.getCurrentProfile();
        CrashAnalysisEngine.Report report = CrashAnalysisEngine.analyze(crashProfile, exitCode, details);
        CrashAnalysisEngine.Finding primaryFinding = report.findings.isEmpty() ? null : report.findings.get(0);
        if (primaryFinding != null) {
            message += "\n\n" + primaryFinding.title + "\n" + primaryFinding.recommendation;
        }
        androidx.appcompat.app.AlertDialog.Builder builder = Tools.createStyledDialogBuilder(this)
                .setTitle(R.string.minecraft_crash_title)
                .setMessage(message)
                .setPositiveButton(R.string.minecraft_crash_view_logs,
                        (dialog, which) -> Tools.swapFragment(this, LogViewerFragment.class, LogViewerFragment.TAG, null))
                .setNegativeButton(android.R.string.ok, null);
        if (primaryFinding != null) {
            builder.setNeutralButton(R.string.crash_apply_recovery, (dialog, which) -> {
                try {
                    if (CrashAnalysisEngine.applyRecovery(crashProfile, primaryFinding)) {
                        LauncherProfiles.write();
                        Toast.makeText(this, R.string.crash_recovery_applied, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.crash_recovery_manual, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception exception) {
                    Tools.showError(this, exception);
                }
            });
        }
        Tools.showStyledDialog(builder);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        inspectReturnedGameSession();
        applyLauncherBackground();
        BattlyWorldsInvites.heartbeat(this);
        BattlySocialManager.heartbeatLauncher(this);
        if (mInstallTracker != null) {
            mInstallTracker.attach();
        }
        BattlyInAppMessaging.showPendingIfAny(this);
        maybeRunStartupPrompts();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
        if (mInstallTracker != null) {
            mInstallTracker.detach();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        if (mProgressServiceKeeper != null) {
            ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        }
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
        if (mBattlyInAppReceiver != null) {
            try {
                unregisterReceiver(mBattlyInAppReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            mBattlyInAppReceiver = null;
        }
        stopAnimatedBackground();
    }

    private void registerBattlyInAppReceiver() {
        if (mBattlyInAppReceiver != null) {
            return;
        }
        mBattlyInAppReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                BattlyInAppMessaging.showFromIntent(LauncherActivity.this, intent);
            }
        };
        IntentFilter filter = new IntentFilter(BattlyInAppMessaging.ACTION_SHOW_IN_APP_MESSAGE);
        ContextCompat.registerReceiver(this, mBattlyInAppReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
        if (currentFragment instanceof SelectAuthFragment && PojavProfile.getAllProfilesList().isEmpty()) {
            return;
        }

        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if (fragment != null) {
            if (fragment.canGoBack()) {
                fragment.goBack();
                return;
            }
        }

        // Check if we are at the root then
        if (getVisibleFragment("ROOT") != null) {
            finish();
        }

        super.onBackPressed();
    }

    @Override
    public void onAttachedToWindow() {
        LauncherPreferences.computeNotchSize(this);
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id) {
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if (fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    private void checkNotificationPermission() {
        checkNotificationPermission(null);
    }

    private void checkNotificationPermission(Runnable afterDone) {
        if (LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
                checkForNotificationPermission()) {
            if (afterDone != null) {
                afterDone.run();
            }
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.POST_NOTIFICATIONS)) {
            showNotificationPermissionReasoning(afterDone);
            return;
        }
        askForNotificationPermission(afterDone);
    }

    private void showNotificationPermissionReasoning(Runnable afterDone) {
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) -> askForNotificationPermission(afterDone))
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    handleNoNotificationPermission();
                    if (afterDone != null) {
                        afterDone.run();
                    }
                }));
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
        Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show();
    }

    public boolean checkForNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_DENIED;
    }

    public boolean checkForMicrophonePermission() {
        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_DENIED;
    }

    public void askForNotificationPermission(Runnable onSuccessRunnable) {
        if (Build.VERSION.SDK_INT < 33)
            return;
        if (onSuccessRunnable != null) {
            mRequestNotificationPermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    public void askForMicrophonePermission(Runnable onSuccessRunnable) {
        if (onSuccessRunnable != null) {
            mRequestMicrophonePermissionRunnable = new WeakReference<>(onSuccessRunnable);
        }
        mRequestMicrophonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    /** Stuff all the view boilerplate here */
    private void bindViews() {
        mFragmentView = findViewById(R.id.container_fragment);
        mLauncherBackground = findViewById(R.id.launcher_background);
        mLauncherBackgroundNext = findViewById(R.id.launcher_background_next);
        mLauncherBackgroundVideo = findViewById(R.id.launcher_background_video);
        applyLauncherBackground();
        mSettingsButton = findViewById(R.id.setting_button);
        mAccountSpinner = findViewById(R.id.account_spinner);
        mProgressLayout = findViewById(R.id.progress_layout);
        mLauncherHeader = findViewById(R.id.launcher_header);
        mLauncherOverlay = findViewById(R.id.launcher_overlay);
        mLauncherScrim = findViewById(R.id.launcher_scrim);
        mBrandBlock = findViewById(R.id.brand_block);
        mTopActions = findViewById(R.id.top_actions);
        mPanelNavigation = findViewById(R.id.launcher_panel_navigation);
        mBackButton = findViewById(R.id.launcher_back_button);
        mHomeButton = findViewById(R.id.launcher_home_button);
        mPanelTitle = findViewById(R.id.launcher_panel_title);
    }

    private void updateLauncherChrome(@NonNull Fragment fragment) {
        boolean isMainMenu = fragment instanceof MainMenuFragment;
        boolean isWelcomePanel = fragment instanceof BattlyPlusWelcomeFragment;
        boolean isSettingsPanel = fragment instanceof LauncherPreferenceFragment
                || fragment instanceof RuntimeManagerFragment
                || fragment instanceof BackgroundSettingsFragment
                || fragment instanceof CreditsFragment
                || fragment instanceof BattlyPlusFragment
                || fragment instanceof BattlyIconSettingsFragment
                || fragment instanceof ControlHubFragment;
        boolean isControlMarketplace = fragment instanceof ControlMarketplaceFragment;
        boolean isAuthSelect = fragment instanceof SelectAuthFragment;
        int horizontalPadding = isMainMenu ? dpToPx(22) : (isSettingsPanel ? dpToPx(18) : 0);
        int topPadding = getResources().getDimensionPixelSize(R.dimen.launcher_header_top_padding);
        int bottomPadding = isMainMenu ? dpToPx(18) : 0;

        mSettingsButton.setImageDrawable(ContextCompat.getDrawable(
                getBaseContext(),
                isMainMenu ? R.drawable.ic_sharp_settings_24 : R.drawable.ic_menu_home));
        mLauncherHeader.setVisibility((isWelcomePanel || isControlMarketplace || isAuthSelect) ? View.GONE : View.VISIBLE);
        mBrandBlock.setVisibility(isMainMenu ? View.VISIBLE : View.GONE);
        mTopActions.setVisibility(isMainMenu ? View.VISIBLE : View.GONE);
        mPanelNavigation.setVisibility((isWelcomePanel || isMainMenu || isControlMarketplace || isAuthSelect) ? View.GONE : View.VISIBLE);
        if (!isWelcomePanel && !isMainMenu && !isControlMarketplace && !isAuthSelect) {
            mPanelTitle.setText(getPanelTitle(fragment));
        }
        mLauncherOverlay.setPadding(isWelcomePanel ? 0 : horizontalPadding,
                (isWelcomePanel || isControlMarketplace || isAuthSelect) ? 0 : topPadding,
                isWelcomePanel ? 0 : horizontalPadding,
                isWelcomePanel ? 0 : bottomPadding);
    }

    public void applyLauncherBackground() {
        stopAnimatedBackground();
        stopVideoBackground();
        BattlyBackgrounds.applySelectedBackground(this, mLauncherBackground);
        if (mLauncherBackgroundNext != null) {
            mLauncherBackgroundNext.setVisibility(View.GONE);
            mLauncherBackgroundNext.setAlpha(0f);
        }
        if (BattlyBackgrounds.isCustomVideoSelected(this)) {
            startVideoBackground();
            return;
        }
        if (BattlyBackgrounds.isAnimatedSelected(this)) {
            startAnimatedBackground();
        }
    }

    private void startVideoBackground() {
        if (mLauncherBackgroundVideo == null) {
            return;
        }
        mLauncherBackgroundVideo.setVideoURI(BattlyBackgrounds.getCustomBackgroundUri(this));
        mLauncherBackgroundVideo.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            mp.setVolume(0f, 0f);
            mLauncherBackgroundVideo.setVisibility(View.VISIBLE);
            mLauncherBackgroundVideo.start();
        });
        mLauncherBackgroundVideo.setOnErrorListener((mp, what, extra) -> {
            stopVideoBackground();
            BattlyBackgrounds.applySelectedBackground(this, mLauncherBackground);
            return true;
        });
    }

    private void stopVideoBackground() {
        if (mLauncherBackgroundVideo == null) {
            return;
        }
        try {
            mLauncherBackgroundVideo.stopPlayback();
        } catch (Throwable ignored) {
        }
        mLauncherBackgroundVideo.setVisibility(View.GONE);
    }

    private void startAnimatedBackground() {
        if (mLauncherBackground == null || mLauncherBackgroundNext == null) {
            return;
        }
        int[] resources = BattlyBackgrounds.getAnimatedBackgroundResources();
        if (resources.length < 2) {
            return;
        }
        mAnimatedBackgroundIndex = 0;
        mLauncherBackground.setImageResource(resources[mAnimatedBackgroundIndex]);
        mLauncherBackground.setScaleX(1.04f);
        mLauncherBackground.setScaleY(1.04f);
        mLauncherBackground.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .setDuration(7600)
                .start();
        mBackgroundAnimationRunnable = () -> {
            int[] currentResources = BattlyBackgrounds.getAnimatedBackgroundResources();
            if (!BattlyBackgrounds.isAnimatedSelected(this) || currentResources.length < 2) {
                stopAnimatedBackground();
                BattlyBackgrounds.applySelectedBackground(this, mLauncherBackground);
                return;
            }
            mAnimatedBackgroundIndex = (mAnimatedBackgroundIndex + 1) % currentResources.length;
            mLauncherBackgroundNext.setImageResource(currentResources[mAnimatedBackgroundIndex]);
            mLauncherBackgroundNext.setScaleX(1.04f);
            mLauncherBackgroundNext.setScaleY(1.04f);
            mLauncherBackgroundNext.setAlpha(0f);
            mLauncherBackgroundNext.setVisibility(View.VISIBLE);
            mLauncherBackgroundNext.animate()
                    .alpha(1f)
                    .scaleX(1.1f)
                    .scaleY(1.1f)
                    .setDuration(1200)
                    .withEndAction(() -> {
                        mLauncherBackground.setImageResource(currentResources[mAnimatedBackgroundIndex]);
                        mLauncherBackground.setScaleX(1.04f);
                        mLauncherBackground.setScaleY(1.04f);
                        mLauncherBackground.animate()
                                .scaleX(1.1f)
                                .scaleY(1.1f)
                                .setDuration(7600)
                                .start();
                        mLauncherBackgroundNext.setVisibility(View.GONE);
                        mLauncherBackgroundNext.setAlpha(0f);
                    })
                    .start();
            mBackgroundAnimationHandler.postDelayed(mBackgroundAnimationRunnable, 8200);
        };
        mBackgroundAnimationHandler.postDelayed(mBackgroundAnimationRunnable, 8200);
    }

    private void stopAnimatedBackground() {
        if (mBackgroundAnimationRunnable != null) {
            mBackgroundAnimationHandler.removeCallbacks(mBackgroundAnimationRunnable);
            mBackgroundAnimationRunnable = null;
        }
        if (mLauncherBackground != null) {
            mLauncherBackground.animate().cancel();
            mLauncherBackground.setScaleX(1f);
            mLauncherBackground.setScaleY(1f);
            mLauncherBackground.setAlpha(1f);
        }
        if (mLauncherBackgroundNext != null) {
            mLauncherBackgroundNext.animate().cancel();
            mLauncherBackgroundNext.setScaleX(1f);
            mLauncherBackgroundNext.setScaleY(1f);
            mLauncherBackgroundNext.setAlpha(0f);
        }
    }

    private CharSequence getPanelTitle(@NonNull Fragment fragment) {
        if (fragment instanceof DownloadCenterFragment) {
            return getString(R.string.download_panel_title);
        }
        if (fragment instanceof DownloadVersionSelectorFragment
                || fragment instanceof VanillaInstallFragment
                || fragment instanceof FabricInstallFragment
                || fragment instanceof QuiltInstallFragment
                || fragment instanceof ForgeInstallFragment
                || fragment instanceof OptiFineInstallFragment
                || fragment instanceof NeoForgeInstallFragment
                || fragment instanceof ModVersionListFragment
                || fragment instanceof FabriclikeInstallFragment) {
            return getString(R.string.download_action_versions_title);
        }
        if (fragment instanceof SearchModFragment) {
            return getSearchPanelTitle(fragment.getArguments());
        }
        if (fragment instanceof LogViewerFragment) {
            return getString(R.string.log_viewer_title);
        }
        if (fragment instanceof RuntimeManagerFragment) {
            return getString(R.string.multirt_config_title);
        }
        if (fragment instanceof BackgroundSettingsFragment) {
            return getString(R.string.battly_background_settings_title);
        }
        if (fragment instanceof BattlyPlusFragment) {
            return getString(R.string.battly_plus_title);
        }
        if (fragment instanceof BattlyIconSettingsFragment) {
            return getString(R.string.battly_plus_app_icon_title);
        }
        if (fragment instanceof ControlHubFragment) {
            return getString(R.string.control_hub_title);
        }
        if (fragment instanceof CreditsFragment) {
            return getString(R.string.launcher_open_credits);
        }
        if (fragment instanceof SelectAuthFragment
                || fragment instanceof MicrosoftLoginFragment
                || fragment instanceof LocalLoginFragment
                || fragment instanceof BattlyLoginFragment) {
            return getString(R.string.account_manager_title);
        }
        if (fragment instanceof ProfileEditorFragment) {
            return getString(R.string.profile_editor_title);
        }
        if (fragment instanceof ProfileTypeSelectFragment || fragment instanceof ModpackCreateFragment) {
            return getString(R.string.profile_manager_title);
        }
        if (fragment instanceof LauncherPreferenceVideoFragment) {
            return getString(R.string.preference_video_title);
        }
        if (fragment instanceof LauncherPreferenceControlFragment) {
            return getString(R.string.preference_control_title);
        }
        if (fragment instanceof LauncherPreferenceJavaFragment) {
            return getString(R.string.preference_java_title);
        }
        if (fragment instanceof LauncherPreferenceMiscellaneousFragment) {
            return getString(R.string.preference_misc_title);
        }
        if (fragment instanceof LauncherPreferenceExperimentalFragment) {
            return getString(R.string.preference_experimental_title);
        }
        if (fragment instanceof LauncherPreferenceRendererSettingsFragment) {
            return getString(R.string.mcl_setting_title_renderer_settings);
        }
        if (fragment instanceof BattlySkinManagerFragment) {
            return getString(R.string.battly_skins_title);
        }
        if (fragment instanceof BattlySocialFragment) {
            return getString(R.string.battly_social_title);
        }
        if (fragment instanceof BattlyFileManagerFragment) {
            return getString(R.string.battly_files_title);
        }
        if (fragment instanceof LauncherPreferenceFragment) {
            return getString(R.string.settings_panel_title);
        }
        if (fragment instanceof GamepadMapperFragment) {
            return getString(R.string.mcl_option_customcontrol);
        }
        return getString(R.string.launcher_nav_workspace);
    }

    private CharSequence getSearchPanelTitle(Bundle arguments) {
        int contentType = arguments == null
                ? SearchFilters.TYPE_MODPACK
                : arguments.getInt(SearchModFragment.ARG_CONTENT_TYPE, SearchFilters.TYPE_MODPACK);
        switch (contentType) {
            case SearchFilters.TYPE_MOD:
                return getString(R.string.download_action_browse_mods_title);
            case SearchFilters.TYPE_RESOURCEPACK:
                return getString(R.string.download_action_browse_resourcepacks_title);
            case SearchFilters.TYPE_SHADER:
                return getString(R.string.download_action_browse_shaders_title);
            case SearchFilters.TYPE_DATAPACK:
                return getString(R.string.download_action_browse_datapacks_title);
            case SearchFilters.TYPE_MODPACK:
            default:
                return getString(R.string.download_action_browse_modpacks_title);
        }
    }

    public void showMainMenuProgress() {
        Tools.backToMainMenu(this);
        mProgressLayout.post(() -> mProgressLayout.setExpanded(true));
    }

    public void refreshHomeProfileUi() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.container_fragment);
        if (fragment instanceof MainMenuFragment) {
            ((MainMenuFragment) fragment).refreshLauncherProfileUi();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
