package com.gamestate.monitor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * FrameTimeDistributionBarView
 * ----------------------------
 * Multi-segmented horizontal bar rendering real-time frame pacing distribution:
 * - Smooth (<16.6ms): Cyan (#00D2E0)
 * - Minor Stutters (16.6ms - 33.3ms): Deep Cyan (#00838F)
 * - Major Stutters (>33.3ms): Alert Coral/Red (#FF5252)
 */
public class FrameTimeDistributionBarView extends View {

    private Paint paintSmooth;
    private Paint paintMinor;
    private Paint paintMajor;
    private Paint paintTrack;

    private RectF bounds = new RectF();
    private float cornerRadiusPx = 0f;

    private float smoothPct = 96.0f;
    private float minorPct = 3.2f;
    private float majorPct = 0.8f;

    public FrameTimeDistributionBarView(Context context) {
        super(context);
        init();
    }

    public FrameTimeDistributionBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FrameTimeDistributionBarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        cornerRadiusPx = 4f * density;

        paintTrack = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintTrack.setColor(Color.parseColor("#16161E"));
        paintTrack.setStyle(Paint.Style.FILL);

        paintSmooth = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintSmooth.setColor(Color.parseColor("#00D2E0")); // Cyan
        paintSmooth.setStyle(Paint.Style.FILL);

        paintMinor = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintMinor.setColor(Color.parseColor("#00838F")); // Deep Cyan / Teal
        paintMinor.setStyle(Paint.Style.FILL);

        paintMajor = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintMajor.setColor(Color.parseColor("#FF5252")); // Red / Stutter alert
        paintMajor.setStyle(Paint.Style.FILL);
    }

    public void setDistribution(float smooth, float minor, float major) {
        float total = smooth + minor + major;
        if (total <= 0f) {
            this.smoothPct = 100f;
            this.minorPct = 0f;
            this.majorPct = 0f;
        } else {
            this.smoothPct = (smooth / total) * 100f;
            this.minorPct = (minor / total) * 100f;
            this.majorPct = (major / total) * 100f;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bounds.set(0, 0, w, h);

        // Clip or draw background track
        canvas.drawRoundRect(bounds, cornerRadiusPx, cornerRadiusPx, paintTrack);

        float smoothWidth = (smoothPct / 100f) * w;
        float minorWidth = (minorPct / 100f) * w;
        float majorWidth = w - smoothWidth - minorWidth;

        // Smooth segment (left)
        if (smoothWidth > 0) {
            RectF rSmooth = new RectF(0, 0, smoothWidth, h);
            canvas.drawRoundRect(rSmooth, cornerRadiusPx, cornerRadiusPx, paintSmooth);
        }

        // Minor segment (middle)
        if (minorWidth > 0) {
            RectF rMinor = new RectF(smoothWidth, 0, smoothWidth + minorWidth, h);
            canvas.drawRect(rMinor, paintMinor);
        }

        // Major segment (right)
        if (majorWidth > 0 && majorPct > 0) {
            RectF rMajor = new RectF(smoothWidth + minorWidth, 0, w, h);
            canvas.drawRoundRect(rMajor, cornerRadiusPx, cornerRadiusPx, paintMajor);
        }
    }
}
