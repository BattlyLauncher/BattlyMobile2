package net.kdt.pojavlaunch.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class TouchCalibrationView extends View {
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path trail = new Path();
    private float lastX;
    private float lastY;
    private float distance;
    private int samples;
    private Listener listener;

    public TouchCalibrationView(Context context) {
        this(context, null);
    }

    public TouchCalibrationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setBackgroundColor(Color.rgb(15, 34, 48));
        gridPaint.setColor(Color.argb(80, 142, 210, 229));
        gridPaint.setStrokeWidth(dp(1));
        trailPaint.setColor(Color.rgb(130, 224, 201));
        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);
        trailPaint.setStrokeWidth(dp(4));
        pointPaint.setColor(Color.rgb(255, 211, 63));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void reset() {
        trail.reset();
        distance = 0;
        samples = 0;
        invalidate();
        notifyListener();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        canvas.drawLine(cx, 0, cx, getHeight(), gridPaint);
        canvas.drawLine(0, cy, getWidth(), cy, gridPaint);
        canvas.drawCircle(cx, cy, dp(22), gridPaint);
        canvas.drawPath(trail, trailPaint);
        if (samples > 0) canvas.drawCircle(lastX, lastY, dp(7), pointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                trail.moveTo(x, y);
                samples++;
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getHistorySize(); i++) {
                    addPoint(event.getHistoricalX(i), event.getHistoricalY(i));
                }
                addPoint(x, y);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                addPoint(x, y);
                break;
            default:
                return true;
        }
        invalidate();
        notifyListener();
        return true;
    }

    private void addPoint(float x, float y) {
        distance += Math.hypot(x - lastX, y - lastY);
        lastX = x;
        lastY = y;
        trail.lineTo(x, y);
        samples++;
    }

    private void notifyListener() {
        if (listener != null) listener.onSamplesChanged(samples, distance);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    public interface Listener {
        void onSamplesChanged(int samples, float distancePx);
    }
}
