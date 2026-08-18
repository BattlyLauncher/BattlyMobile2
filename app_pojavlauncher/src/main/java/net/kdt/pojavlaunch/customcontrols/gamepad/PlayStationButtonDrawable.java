package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Lightweight vector-style PlayStation face glyphs drawn from primitives. */
public final class PlayStationButtonDrawable extends Drawable {
    public enum Symbol { CROSS, CIRCLE, SQUARE, TRIANGLE }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Symbol symbol;

    public PlayStationButtonDrawable(Symbol symbol) {
        this.symbol = symbol;
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        float cx = getBounds().exactCenterX();
        float cy = getBounds().exactCenterY();
        float radius = Math.min(getBounds().width(), getBounds().height()) * 0.29f;
        paint.setStrokeWidth(Math.max(3f, radius * 0.18f));
        switch (symbol) {
            case CROSS:
                canvas.drawLine(cx - radius, cy - radius, cx + radius, cy + radius, paint);
                canvas.drawLine(cx + radius, cy - radius, cx - radius, cy + radius, paint);
                break;
            case CIRCLE:
                canvas.drawCircle(cx, cy, radius, paint);
                break;
            case SQUARE:
                canvas.drawRect(cx - radius, cy - radius, cx + radius, cy + radius, paint);
                break;
            case TRIANGLE:
                Path path = new Path();
                path.moveTo(cx, cy - radius);
                path.lineTo(cx + radius, cy + radius);
                path.lineTo(cx - radius, cy + radius);
                path.close();
                canvas.drawPath(path, paint);
                break;
        }
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
