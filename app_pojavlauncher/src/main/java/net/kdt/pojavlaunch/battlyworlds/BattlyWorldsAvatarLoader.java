package net.kdt.pojavlaunch.battlyworlds;

import android.graphics.Bitmap;
import android.widget.ImageView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlySkinApi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BattlyWorldsAvatarLoader {
    private static final Map<String, Bitmap> CACHE = new ConcurrentHashMap<>();

    public static void load(ImageView target, String username) {
        if (target == null) return;
        String key = username == null ? "" : username.trim();
        target.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null && !cached.isRecycled()) {
            target.setImageBitmap(cached);
            return;
        }
        target.setImageResource(R.drawable.ic_battly_social);
        if (key.isEmpty()) return;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Bitmap bitmap = BattlySkinApi.downloadFaceBitmap(key);
                if (bitmap == null || bitmap.isRecycled()) return;
                CACHE.put(key, bitmap);
                Tools.runOnUiThread(() -> {
                    if (key.equals(target.getTag())) target.setImageBitmap(bitmap);
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private BattlyWorldsAvatarLoader() {
    }
}
