package net.kdt.pojavlaunch.battlyworlds;

import android.graphics.Bitmap;
import android.util.LruCache;
import android.widget.ImageView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BattlySkinApi;

public final class BattlyWorldsAvatarLoader {
    private static final int CACHE_SIZE_KB = 4 * 1024;
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return Math.max(1, bitmap.getAllocationByteCount() / 1024);
        }
    };

    public static void load(ImageView target, String username) {
        if (target == null) return;
        String key = username == null ? "" : username.trim();
        target.setTag(key);
        Bitmap cached;
        synchronized (CACHE) {
            cached = CACHE.get(key);
        }
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
                synchronized (CACHE) {
                    CACHE.put(key, bitmap);
                }
                Tools.runOnUiThread(() -> {
                    if (key.equals(target.getTag())) target.setImageBitmap(bitmap);
                });
            } catch (Throwable ignored) {
            }
        });
    }

    public static void trimMemory() {
        synchronized (CACHE) {
            CACHE.evictAll();
        }
    }

    private BattlyWorldsAvatarLoader() {
    }
}
