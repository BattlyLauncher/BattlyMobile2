package net.kdt.pojavlaunch.fragments;

import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.ForgeVersionListHandler;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.NeoForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.NeoForgeVersionListAdapter;
import net.kdt.pojavlaunch.modloaders.NeoForgeVersionUtils;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class NeoForgeInstallFragment extends ModVersionListFragment<List<String>> {
    public static final String TAG = "NeoForgeInstallFragment";
    public NeoForgeInstallFragment() {
        super(TAG);
    }

    private static final String NEOFORGE_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

    @Override
    public int getTitleText() {
        return R.string.neoforge_dl_select_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.neoforge_dl_no_installer;
    }

    @Override
    public List<String> loadVersionList() {
        return downloadNeoForgeVersions();
    }

    public static List<String> downloadNeoForgeVersions() {
        SAXParser saxParser;
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            saxParser = parserFactory.newSAXParser();
        }catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            // if we cant make a parser we might as well not even try to parse anything
            return null;
        }
        try {
            //of_test();
            return DownloadUtils.downloadStringCached(NEOFORGE_METADATA_URL, "neoforge_versions", input -> {
                try {
                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    return handler.getVersions();
                    // IOException is present here StringReader throws it only if the parser called close()
                    // sooner than needed, which is a parser issue and not an I/O one
                }catch (SAXException | IOException e) {
                    throw new DownloadUtils.ParseException(e);
                }
            });
        }catch (DownloadUtils.ParseException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    public ExpandableListAdapter createAdapter(List<String> versionList, LayoutInflater layoutInflater) {
        return new NeoForgeVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    protected List<String> filterVersionList(List<String> versionList, String query) {
        if (query == null || query.trim().isEmpty()) {
            return versionList;
        }
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        ArrayList<String> filtered = new ArrayList<>();
        for (String version : versionList) {
            String minecraftVersion = NeoForgeVersionUtils.toMinecraftVersion(version);
            if ((version != null && version.toLowerCase(Locale.ROOT).contains(normalized))
                    || (minecraftVersion != null && minecraftVersion.toLowerCase(Locale.ROOT).contains(normalized))) {
                filtered.add(version);
            }
        }
        return filtered;
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderDownloadListener listener) {
        return new NeoForgeDownloadTask(requireContext(), listener, (String) selectedVersion);
    }

    @Override
    protected String extractVanillaVersion(Object selectedVersion) {
        // NeoForge version format: "21.4.52" → MC version "1.21.4"
        return NeoForgeVersionUtils.toMinecraftVersion((String) selectedVersion);
    }

    @Override
    protected String getSuccessMessageLabel(Object selectedVersion) {
        return "NeoForge";
    }
}
