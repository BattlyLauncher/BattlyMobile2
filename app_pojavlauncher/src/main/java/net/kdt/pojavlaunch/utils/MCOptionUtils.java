package net.kdt.pojavlaunch.utils;
import static org.lwjgl.glfw.CallbackBridge.windowHeight;
import static org.lwjgl.glfw.CallbackBridge.windowWidth;

import android.os.Build;
import android.os.FileObserver;
import android.util.Log;
import android.util.AtomicFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class MCOptionUtils {
    private static final HashMap<String,String> sParameterMap = new HashMap<>();
    private static final LinkedHashMap<String,String> sPendingUpdates = new LinkedHashMap<>();
    private static final ArrayList<WeakReference<MCOptionListener>> sOptionListeners = new ArrayList<>();
    private static FileObserver sFileObserver;
    private static String sOptionFolderPath = null;
    public interface MCOptionListener {
        /** Called when an option is changed. Don't know which one though */
        void onOptionChanged();
    }


    public static synchronized void load(){
        load(sOptionFolderPath == null
                ? Tools.DIR_GAME_NEW
                : sOptionFolderPath);
    }

    public static synchronized void load(@NonNull String folderPath) {
        File optionFile = new File(folderPath + "/options.txt");
        if(!optionFile.exists()) {
            try { // Needed for new instances I guess  :think:
                optionFile.createNewFile();
            } catch (IOException e) { e.printStackTrace(); }
        }

        if(sFileObserver == null || !Objects.equals(sOptionFolderPath, folderPath)){
            if (sFileObserver != null) sFileObserver.stopWatching();
            sOptionFolderPath = folderPath;
            setupFileObserver();
        }
        sOptionFolderPath = folderPath; // Yeah I know, it may be redundant

        sParameterMap.clear();
        sPendingUpdates.clear();

        try {
            BufferedReader reader = new BufferedReader(new FileReader(optionFile));
            String line;
            while ((line = reader.readLine()) != null) {
                int firstColonIndex = line.indexOf(':');
                if(firstColonIndex < 0) {
                    Log.w(Tools.APP_NAME, "No colon on line \""+line+"\", skipping");
                    continue;
                }
                sParameterMap.put(line.substring(0,firstColonIndex), line.substring(firstColonIndex+1));
            }
            reader.close();
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not load options.txt", e);
        }
    }

    public static synchronized void set(String key, String value) {
        sParameterMap.put(key,value);
        sPendingUpdates.put(key, value);
    }

    /** Set an array of String, instead of a simple value. Not supported on all options */
    public static synchronized void set(String key, List<String> values){
        set(key, values.toString());
    }

    public static synchronized String get(String key){
        return sParameterMap.get(key);
    }

    /** @return A list of values from an array stored as a string */
    public static List<String> getAsList(String key){
        String value = get(key);

        // Fallback if the value doesn't exist
        if (value == null) return new ArrayList<>();

        // Remove the edges
        value = value.replace("[", "").replace("]", "");
        if (value.isEmpty()) return new ArrayList<>();

        return Arrays.asList(value.split(","));
    }

    public static synchronized void save() {
        if (sOptionFolderPath == null || sPendingUpdates.isEmpty()) return;
        File optionFile = new File(sOptionFolderPath, "options.txt");
        AtomicFile atomicFile = new AtomicFile(optionFile);
        FileOutputStream output = null;
        try {
            String current = optionFile.isFile() ? Tools.read(optionFile) : "";
            String merged = MinecraftOptionsFile.merge(current, sPendingUpdates);
            if (sFileObserver != null) sFileObserver.stopWatching();
            output = atomicFile.startWrite();
            output.write(merged.getBytes(StandardCharsets.UTF_8));
            atomicFile.finishWrite(output);
            output = null;
            sPendingUpdates.clear();
            reloadParameters(optionFile);
            setupFileObserver();
        } catch (IOException e) {
            if (output != null) atomicFile.failWrite(output);
            Log.w(Tools.APP_NAME, "Could not save options.txt", e);
            setupFileObserver();
        }
    }

    private static void reloadParameters(File optionFile) {
        sParameterMap.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(optionFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int firstColonIndex = line.indexOf(':');
                if (firstColonIndex < 0) continue;
                sParameterMap.put(line.substring(0, firstColonIndex), line.substring(firstColonIndex + 1));
            }
        } catch (IOException e) {
            Log.w(Tools.APP_NAME, "Could not reload options.txt", e);
        }
    }

    /** @return The stored Minecraft GUI scale, also auto-computed if on auto-mode or improper setting */
    public static int getMcScale() {
        String str = MCOptionUtils.get("guiScale");
        int guiScale = (str == null ? 0 :Integer.parseInt(str));

        int scale = Math.max(Math.min(windowWidth / 320, windowHeight / 240), 1);
        if(scale < guiScale || guiScale == 0){
            guiScale = scale;
        }

        return guiScale;
    }

    /** Add a file observer to reload options on file change
     * Listeners get notified of the change */
    private static void setupFileObserver(){
        if (sOptionFolderPath == null) return;
        if (sFileObserver != null) sFileObserver.stopWatching();
        final int events = FileObserver.MODIFY | FileObserver.CLOSE_WRITE
                | FileObserver.CREATE | FileObserver.MOVED_TO;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q){
            sFileObserver = new FileObserver(new File(sOptionFolderPath), events) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    onObservedFileChange(s);
                }
            };
        }else{
            sFileObserver = new FileObserver(sOptionFolderPath, events) {
                @Override
                public void onEvent(int i, @Nullable String s) {
                    onObservedFileChange(s);
                }
            };
        }

        sFileObserver.startWatching();
    }

    private static void onObservedFileChange(@Nullable String path) {
        if (path != null && !"options.txt".equals(path)) return;
        synchronized (MCOptionUtils.class) {
            if (sOptionFolderPath == null) return;
            LinkedHashMap<String, String> pending = new LinkedHashMap<>(sPendingUpdates);
            reloadParameters(new File(sOptionFolderPath, "options.txt"));
            sParameterMap.putAll(pending);
        }
        notifyListeners();
    }

    /** Notify the option listeners */
    public static void notifyListeners(){
        List<MCOptionListener> listeners = new ArrayList<>();
        synchronized (sOptionListeners) {
            Iterator<WeakReference<MCOptionListener>> iterator = sOptionListeners.iterator();
            while (iterator.hasNext()) {
                MCOptionListener listener = iterator.next().get();
                if (listener == null) iterator.remove();
                else listeners.add(listener);
            }
        }
        for (MCOptionListener listener : listeners) listener.onOptionChanged();
    }

    /** Add an option listener, notice how we don't have a reference to it */
    public static void addMCOptionListener(MCOptionListener listener){
        if (listener == null) return;
        synchronized (sOptionListeners) {
            Iterator<WeakReference<MCOptionListener>> iterator = sOptionListeners.iterator();
            while (iterator.hasNext()) {
                MCOptionListener existing = iterator.next().get();
                if (existing == null) iterator.remove();
                else if (existing == listener) return;
            }
            sOptionListeners.add(new WeakReference<>(listener));
        }
    }

    /** Remove a listener from existence, or at least, its reference here */
    public static void removeMCOptionListener(MCOptionListener listener){
        synchronized (sOptionListeners) {
            Iterator<WeakReference<MCOptionListener>> iterator = sOptionListeners.iterator();
            while (iterator.hasNext()) {
                MCOptionListener existing = iterator.next().get();
                if (existing == null || existing == listener) {
                    iterator.remove();
                }
            }
        }
    }

}
