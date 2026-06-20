package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.LocaleUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";
    private static final String BATLLY_NEWS_URL_BASE = "https://api.battlylauncher.com/battlylauncher/launcher/news-launcher/mobile.";
    private static final Object NEWS_LOCK = new Object();
    private static List<NewsCard> sCachedNewsCards;
    private static String sCachedNewsLocale;
    private static boolean sNewsRequestInFlight;

    private mcVersionSpinner mVersionSpinner;
    private View mPlayButton;
    private TextView mPlayButtonTitle;
    private ProgressBar mPlayButtonProgress;
    private TextView mSelectedVersionLabel;
    private RecyclerView mNewsPager;
    private NewsCardAdapter mNewsCardAdapter;
    private boolean mLaunchStarting;

    private final ExtraListener<Boolean> mLaunchUiResetListener = (key, value) -> {
        Tools.runOnUiThread(() -> setPlayLoading(false));
        return false;
    };

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ImageButton mToolsButton = requireActivity().findViewById(R.id.news_button);
        ImageButton mLibraryButton = requireActivity().findViewById(R.id.library_button);
        View mDownloadButton = view.findViewById(R.id.install_jar_button);
        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        mPlayButton = view.findViewById(R.id.play_button);
        mPlayButtonTitle = view.findViewById(R.id.play_button_title);
        mPlayButtonProgress = view.findViewById(R.id.play_button_progress);
        mSelectedVersionLabel = view.findViewById(R.id.selected_version_label);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        bindNewsPanel(view);
        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME_UI_RESET, mLaunchUiResetListener);

        mToolsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(),
                ControlHubFragment.class, ControlHubFragment.TAG, null));
        mToolsButton.setOnLongClickListener(v -> {
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
        mLibraryButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LibraryCenterFragment.class,
                LibraryCenterFragment.TAG, null));
        mDownloadButton.setOnClickListener(v -> openDownloadCenter());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.performClick());
        mEditProfileButton.setOnLongClickListener(v -> {
            mVersionSpinner.openProfileEditor(requireActivity());
            return true;
        });
        mPlayButton.setOnClickListener(v -> {
            if (mLaunchStarting) {
                return;
            }
            if (Tools.hasMods("sodium") && !(LauncherPreferences.DEFAULT_PREF.getBoolean("sodium_override", false))) {
                AlertDialog sodiumWarningDialog = Tools.createStyledDialogBuilder(requireContext())
                        .setTitle(R.string.sodium_warning_title)
                        .setMessage(R.string.sodium_warning_message)
                        .setIcon(R.drawable.minecraft_tnt)
                        .setPositiveButton(R.string.sodium_launch_anyway, (d, w) -> requestLaunch())
                        .setNegativeButton(android.R.string.cancel, null)
                        .setNeutralButton(R.string.delete_sodium, (d, w) -> {
                            Tools.deleteSodiumMods();
                            requestLaunch();
                        })
                        .create();
                Tools.styleDialog(sodiumWarningDialog);
                sodiumWarningDialog.show();
            } else {
                requestLaunch();
            }
        });

        updateSelectedVersionLabel();
    }

    private void requestLaunch() {
        setPlayLoading(true);
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void setPlayLoading(boolean loading) {
        mLaunchStarting = loading;
        if (mPlayButton != null) {
            mPlayButton.setEnabled(!loading);
            mPlayButton.setAlpha(loading ? 0.88f : 1f);
        }
        if (mPlayButtonTitle != null) {
            mPlayButtonTitle.setText(loading ? R.string.launcher_starting_short : R.string.launcher_execute);
        }
        if (mPlayButtonProgress != null) {
            mPlayButtonProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (mSelectedVersionLabel != null) {
            mSelectedVersionLabel.setVisibility(View.VISIBLE);
            if (loading) {
                mSelectedVersionLabel.setText(R.string.launcher_starting_hint);
            } else {
                updateSelectedVersionLabel();
            }
        }
    }

    private void bindNewsPanel(View view) {
        mNewsPager = view.findViewById(R.id.launcher_news_pager);
        mNewsCardAdapter = new NewsCardAdapter();
        mNewsPager.setLayoutManager(new LinearLayoutManager(requireContext()));
        mNewsPager.setAdapter(mNewsCardAdapter);
        loadNews();
    }

    private void loadNews() {
        String newsLocale = getNewsLocale();
        synchronized (NEWS_LOCK) {
            if (sCachedNewsCards != null && newsLocale.equals(sCachedNewsLocale)) {
                bindNewsCards(sCachedNewsCards);
                return;
            }
            if (sNewsRequestInFlight) {
                setNewsLoading();
                return;
            }
            sNewsRequestInFlight = true;
        }
        setNewsLoading();
        PojavApplication.sExecutorService.execute(() -> {
            List<NewsCard> cards;
            try {
                cards = loadBattlyNews(newsLocale);
            } catch (Exception e) {
                Log.w(TAG, "Failed to load Battly news", e);
                cards = NewsCard.error(getString(R.string.launcher_news_unavailable));
            }
            synchronized (NEWS_LOCK) {
                sCachedNewsCards = cards;
                sCachedNewsLocale = newsLocale;
                sNewsRequestInFlight = false;
            }
            List<NewsCard> finalCards = cards;
            Tools.runOnUiThread(() -> {
                if (!isAdded()) {
                    return;
                }
                bindNewsCards(finalCards);
            });
        });
    }

    private void setNewsLoading() {
        if (mNewsCardAdapter == null) {
            return;
        }
        mNewsCardAdapter.setCards(NewsCard.loading(getString(R.string.launcher_news_loading)));
        mNewsPager.scrollToPosition(0);
    }

    private void bindNewsCards(List<NewsCard> cards) {
        mNewsCardAdapter.setCards(cards);
        mNewsPager.scrollToPosition(0);
    }

    private List<NewsCard> loadBattlyNews(String newsLocale) throws Exception {
        return DownloadUtils.downloadStringFreshWithCacheFallback(
                BATLLY_NEWS_URL_BASE + newsLocale + ".json",
                "battly_mobile_news_" + newsLocale + ".json",
                input -> {
                    try {
                        JsonArray newsArray = Tools.GLOBAL_GSON.fromJson(input, JsonArray.class);
                        if (newsArray == null || newsArray.size() == 0) {
                            return NewsCard.error(getString(R.string.launcher_news_unavailable));
                        }
                        ArrayList<NewsCard> cards = new ArrayList<>();
                        for (int i = 0; i < newsArray.size(); i++) {
                            JsonObject object = newsArray.get(i).getAsJsonObject();
                            cards.add(new NewsCard(
                                    "",
                                    getJsonString(object, "title", getString(R.string.launcher_news_battly)),
                                    getJsonString(object, "content", ""),
                                    getJsonString(object, "author", getString(R.string.launcher_news_unknown_author)),
                                    formatBattlyDate(getJsonString(object, "publish_date", ""))));
                        }
                        return cards;
                    } catch (Exception e) {
                        throw new DownloadUtils.ParseException(e);
                    }
                });
    }

    private String getNewsLocale() {
        String language = LocaleUtils.getCurrentLocale(requireContext()).getLanguage();
        return "es".equalsIgnoreCase(language) ? "es" : "en";
    }

    private String getJsonString(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        String value = object.get(key).getAsString();
        return Tools.isValidString(value) ? value : fallback;
    }

    private String formatBattlyDate(String value) {
        return formatDate(value, "yyyy-MM-dd HH:mm:ss");
    }

    private String formatDate(String value, String sourcePattern) {
        if (!Tools.isValidString(value)) {
            return "";
        }
        try {
            Date date = new SimpleDateFormat(sourcePattern, Locale.US).parse(value);
            return new SimpleDateFormat("d / M / yyyy", Locale.US).format(date);
        } catch (Exception ignored) {
            return value;
        }
    }

    private void openDownloadCenter() {
        Tools.swapFragment(requireActivity(), DownloadCenterFragment.class, DownloadCenterFragment.TAG, null);
    }

    private void openCurrentProfileDirectory() {
        openPath(requireContext(), getCurrentProfileDirectory(), false);
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                null);
        if (!Tools.isValidString(currentProfile))
            return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profileObject == null)
            return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    private void updateSelectedVersionLabel() {
        if (mSelectedVersionLabel == null)
            return;
        LauncherProfiles.load();
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                null);
        MinecraftProfile profile = currentProfile == null ? null
                : LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        String versionName = profile == null ? null : profile.lastVersionId;

        if (MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(versionName)) {
            versionName = getString(R.string.profiles_latest_release);
        } else if (MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(versionName)) {
            versionName = getString(R.string.profiles_latest_snapshot);
        }

        if (!Tools.isValidString(versionName)) {
            versionName = getString(R.string.launcher_version_unknown);
        }

        mSelectedVersionLabel.setText(getString(R.string.launcher_version_format, versionName));
    }

    @Override
    public void onResume() {
        super.onResume();
        setPlayLoading(false);
        refreshLauncherProfileUi();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME_UI_RESET, mLaunchUiResetListener);
        mPlayButton = null;
        mPlayButtonTitle = null;
        mPlayButtonProgress = null;
        mSelectedVersionLabel = null;
        mNewsPager = null;
        mNewsCardAdapter = null;
    }

    public void refreshLauncherProfileUi() {
        if (mVersionSpinner != null) {
            mVersionSpinner.reloadProfiles();
        }
        updateSelectedVersionLabel();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0) {
            Tools.installMod(requireActivity(), isCustomArgs);
        } else {
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
        }
    }

    private void openSearch(int contentType) {
        Tools.swapFragment(
                requireActivity(),
                SearchModFragment.class,
                SearchModFragment.TAG,
                SearchModFragment.createArguments(contentType));
    }

    private static class NewsCard {
        final String source;
        final String title;
        final String content;
        final String author;
        final String dateText;

        NewsCard(String source, String title, String content, String author, String dateText) {
            this.source = source;
            this.title = title;
            this.content = content;
            this.author = author;
            this.dateText = dateText;
        }

        static List<NewsCard> loading(String message) {
            ArrayList<NewsCard> cards = new ArrayList<>();
            cards.add(new NewsCard("", "", message, "", ""));
            return cards;
        }

        static List<NewsCard> error(String message) {
            return loading(message);
        }
    }

    private static class NewsCardAdapter extends RecyclerView.Adapter<NewsCardAdapter.ViewHolder> {
        private final ArrayList<NewsCard> mCards = new ArrayList<>();

        void setCards(List<NewsCard> cards) {
            mCards.clear();
            if (cards != null) {
                mCards.addAll(cards);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_launcher_news_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            NewsCard card = mCards.get(position);
            holder.source.setText(card.source);
            holder.source.setVisibility(Tools.isValidString(card.source) ? View.VISIBLE : View.GONE);
            holder.title.setText(card.title);
            holder.title.setVisibility(Tools.isValidString(card.title) ? View.VISIBLE : View.GONE);
            holder.date.setText(card.dateText);
            holder.date.setVisibility(Tools.isValidString(card.dateText) ? View.VISIBLE : View.GONE);
            holder.bindContent(card.content);
            holder.author.setText(card.author);
            holder.author.setVisibility(Tools.isValidString(card.author) ? View.VISIBLE : View.GONE);
        }

        @Override
        public int getItemCount() {
            return mCards.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final TextView source;
            final TextView title;
            final TextView date;
            final WebView content;
            final TextView author;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                source = itemView.findViewById(R.id.news_card_source);
                title = itemView.findViewById(R.id.news_card_title);
                date = itemView.findViewById(R.id.news_card_date);
                content = itemView.findViewById(R.id.news_card_content);
                author = itemView.findViewById(R.id.news_card_author);
                configureContentWebView(content);
            }

            void bindContent(String rawContent) {
                content.loadDataWithBaseURL(
                        "https://api.battlylauncher.com/",
                        NewsContentFormatter.toHtml(rawContent),
                        "text/html",
                        "UTF-8",
                        null);
            }
        }

        private static void configureContentWebView(WebView webView) {
            webView.setBackgroundColor(0x00000000);
            WebSettings settings = webView.getSettings();
            settings.setJavaScriptEnabled(false);
            settings.setDomStorageEnabled(false);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setBlockNetworkImage(false);
            settings.setLoadsImagesAutomatically(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            webView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                    return openNewsLink(view, request == null ? null : request.getUrl());
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return openNewsLink(view, Tools.isValidString(url) ? Uri.parse(url) : null);
                }

                private boolean openNewsLink(WebView view, Uri uri) {
                    if (uri == null) {
                        return true;
                    }
                    String scheme = uri.getScheme();
                    if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                        view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                    return true;
                }
            });
        }
    }

    private static final class NewsContentFormatter {
        private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?is)<\\s*[a-z][^>]*>");
        private static final Pattern SCRIPT_PATTERN = Pattern.compile("(?is)<script\\b[^>]*>.*?</script>");
        private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile("(?is)\\s+on[a-z]+\\s*=\\s*(['\"]).*?\\1");
        private static final Pattern EVENT_HANDLER_UNQUOTED_PATTERN = Pattern.compile("(?is)\\s+on[a-z]+\\s*=\\s*[^\\s>]+");
        private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)\\]\\((https?://[^\\s)]+)\\)");
        private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("(?<!!)" + "\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)");
        private static final Pattern MARKDOWN_BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");

        static String toHtml(String rawContent) {
            String body = rawContent == null ? "" : rawContent.trim();
            if (!HTML_TAG_PATTERN.matcher(body).find()) {
                body = escapeHtml(body);
                body = MARKDOWN_IMAGE_PATTERN.matcher(body).replaceAll("<img alt=\"$1\" src=\"$2\">");
                body = MARKDOWN_LINK_PATTERN.matcher(body).replaceAll("<a href=\"$2\">$1</a>");
                body = MARKDOWN_BOLD_PATTERN.matcher(body).replaceAll("<strong>$1</strong>");
                body = body.replace("\r\n", "\n").replace("\n", "<br>");
            }
            body = sanitizeHtml(body);
            return "<!doctype html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                    + "<style>"
                    + "html,body{margin:0;padding:0;background:transparent;color:#dcecf6;font:700 12px sans-serif;line-height:1.38;}"
                    + "p{margin:0 0 8px;} span{line-height:1.38;} strong,b{color:#f3faff;} "
                    + "img{display:block;max-width:100%;height:auto;border-radius:12px;margin:10px 0;}"
                    + "a{color:#8debd9;text-decoration:none;font-weight:800;}"
                    + "a.button,.button{display:inline-block;margin-top:10px;padding:9px 14px;border-radius:18px;background:#8debd9;color:#102b31;}"
                    + "</style></head><body>" + body + "</body></html>";
        }

        private static String sanitizeHtml(String html) {
            String sanitized = SCRIPT_PATTERN.matcher(html).replaceAll("");
            sanitized = EVENT_HANDLER_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = EVENT_HANDLER_UNQUOTED_PATTERN.matcher(sanitized).replaceAll("");
            sanitized = sanitized.replaceAll("(?i)javascript\\s*:", "");
            return sanitized;
        }

        private static String escapeHtml(String value) {
            return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
        }
    }
}
