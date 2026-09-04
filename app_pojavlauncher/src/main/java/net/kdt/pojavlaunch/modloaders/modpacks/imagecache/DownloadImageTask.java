package net.kdt.pojavlaunch.modloaders.modpacks.imagecache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.FileOutputStream;
import java.io.IOException;

class DownloadImageTask implements Runnable {
    private final ReadFromDiskTask mParentTask;
    private int mRetryCount;
    DownloadImageTask(ReadFromDiskTask parentTask) {
        this.mParentTask = parentTask;
        this.mRetryCount = 0;
    }

    @Override
    public void run() {
        boolean wasSuccessful = false;
        while(mRetryCount < 5 && !(wasSuccessful = runCatching())) {
            mRetryCount++;
        }
        // restart the parent task to read the image and send it to the receiver
        // if it wasn't cancelled. If it was, then we just die here
        if(wasSuccessful && !mParentTask.taskCancelled())
            mParentTask.iconCache.cacheLoaderPool.execute(mParentTask);
    }

    public boolean runCatching() {
        try {
            IconCacheJanitor.waitForJanitorToFinish();
            DownloadUtils.downloadFile(mParentTask.imageUrl, mParentTask.cacheFile);
            float finalDimension = mParentTask.iconCache.getBitmapFinalDimension();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(mParentTask.cacheFile.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;
            if(bounds.outWidth <= finalDimension && bounds.outHeight <= finalDimension) {
                return true;
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = 1;
            int largestDimension = Math.max(bounds.outWidth, bounds.outHeight);
            while (largestDimension / (decodeOptions.inSampleSize * 2f) > finalDimension * 1.5f) {
                decodeOptions.inSampleSize *= 2;
            }
            Bitmap bitmap = BitmapFactory.decodeFile(mParentTask.cacheFile.getAbsolutePath(), decodeOptions);
            if(bitmap == null) return false;
            int bitmapWidth = bitmap.getWidth(), bitmapHeight = bitmap.getHeight();
            float imageRescaleRatio = Math.min(finalDimension/bitmapWidth, finalDimension/bitmapHeight);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap,
                    (int)(bitmapWidth * imageRescaleRatio),
                    (int)(bitmapHeight * imageRescaleRatio),
                    true);
            if(resizedBitmap != bitmap) bitmap.recycle();
            try (FileOutputStream fileOutputStream = new FileOutputStream(mParentTask.cacheFile)) {
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fileOutputStream);
            } finally {
                resizedBitmap.recycle();
            }
            return true;
        }catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
