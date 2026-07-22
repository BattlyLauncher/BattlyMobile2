package net.kdt.pojavlaunch.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public final class BattlySkinPreviewRenderer {
    private BattlySkinPreviewRenderer() {
    }

    public static Bitmap renderCardPreview(Bitmap skin) {
        return render(skin, 240, 280, false);
    }

    public static Bitmap renderAvatarPreview(Bitmap skin) {
        return render(skin, 360, 320, true);
    }

    private static Bitmap render(Bitmap skin, int width, int height, boolean large) {
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x55000000);
        float shadowWidth = large ? 164 : 136;
        canvas.drawOval(new RectF((width - shadowWidth) / 2f, height - (large ? 38 : 34),
                (width + shadowWidth) / 2f, height - (large ? 16 : 16)), shadowPaint);

        if (skin == null || skin.getWidth() < 64 || skin.getHeight() < 32) {
            drawFallback(canvas, width, height);
            return output;
        }

        Paint pixelPaint = new Paint();
        pixelPaint.setAntiAlias(false);
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setDither(false);

        float scale = large ? 9.2f : 7.2f;
        float bodyTop = large ? 92 : 88;
        float centerX = width / 2f;

        drawPart(canvas, skin, pixelPaint, 20, 20, 8, 12,
                centerX - 4 * scale, bodyTop, scale, 1f);
        drawPart(canvas, skin, pixelPaint, 44, 20, 4, 12,
                centerX - 8.25f * scale, bodyTop + 0.4f * scale, scale, .94f);
        drawPart(canvas, skin, pixelPaint, 44, 20, 4, 12,
                centerX + 4.25f * scale, bodyTop + 0.4f * scale, scale, .94f);
        drawPart(canvas, skin, pixelPaint, 4, 20, 4, 12,
                centerX - 4.15f * scale, bodyTop + 12 * scale, scale, .96f);
        drawPart(canvas, skin, pixelPaint, 4, 20, 4, 12,
                centerX + 0.15f * scale, bodyTop + 12 * scale, scale, .96f);

        float headSize = 8 * scale;
        float headX = centerX - headSize / 2f;
        float headY = bodyTop - headSize + 2 * scale;
        drawPart(canvas, skin, pixelPaint, 8, 8, 8, 8, headX, headY, scale, 1f);
        drawPart(canvas, skin, pixelPaint, 40, 8, 8, 8, headX, headY, scale, .92f);

        return output;
    }

    private static void drawPart(Canvas canvas, Bitmap skin, Paint paint, int x, int y, int w, int h,
                                 float destX, float destY, float scale, float alpha) {
        Rect src = new Rect(x, y, x + w, y + h);
        RectF dst = new RectF(destX, destY, destX + w * scale, destY + h * scale);
        int oldAlpha = paint.getAlpha();
        paint.setAlpha(Math.round(255 * alpha));
        canvas.drawBitmap(skin, src, dst, paint);
        paint.setAlpha(oldAlpha);
    }

    private static void drawFallback(Canvas canvas, int width, int height) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x558DEEDC);
        canvas.drawRoundRect(new RectF(width / 2f - 42, height / 2f - 42,
                width / 2f + 42, height / 2f + 42), 18, 18, paint);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(18);
        paint.setFakeBoldText(true);
        canvas.drawText("Skin", width / 2f, height / 2f + 7, paint);
    }
}
