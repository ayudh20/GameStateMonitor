package com.gamestate.monitor.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * SystemHealthArcView
 * -------------------
 * Custom gaming arc gauge rendering a glowing sweep arc gradient
 * from Neon Green to Cyan with a soft neon halo.
 */
public class SystemHealthArcView extends View {

    private Paint trackPaint;
    private Paint progressPaint;
    private Paint glowPaint;
    private RectF arcBounds;

    private float strokeWidthPx;
    private float progress = 95f; // default high health
    private float animatedProgress = 0f;

    private static final float START_ANGLE = 135f;
    private static final float SWEEP_MAX_ANGLE = 270f;

    public SystemHealthArcView(Context context) {
        super(context);
        init();
    }

    public SystemHealthArcView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SystemHealthArcView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null); // for shadow layer blur

        float density = getResources().getDisplayMetrics().density;
        strokeWidthPx = 10f * density;

        // Dark track arc
        trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidthPx);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#14141C"));

        // Outer neon glow
        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(strokeWidthPx + 6f * density);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setColor(Color.parseColor("#3300D2E0"));

        // Gradient foreground arc
        progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidthPx);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        arcBounds = new RectF();

        animateProgress(progress);
    }

    public void setProgress(float targetProgress) {
        this.progress = Math.max(0f, Math.min(100f, targetProgress));
        animateProgress(this.progress);
    }

    private void animateProgress(float target) {
        ValueAnimator anim = ValueAnimator.ofFloat(animatedProgress, target);
        anim.setDuration(900);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(animation -> {
            animatedProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float pad = strokeWidthPx * 1.4f;
        float diameter = Math.min(w - 2 * pad, h - 2 * pad);
        float cx = w / 2f;
        float cy = h / 2f;
        arcBounds.set(cx - diameter / 2f, cy - diameter / 2f, cx + diameter / 2f, cy + diameter / 2f);

        // Pure Cyan gradient sweep
        int[] colors = new int[]{
                Color.parseColor("#00838F"), // Deep Cyan
                Color.parseColor("#00D2E0"), // Cyber Cyan
                Color.parseColor("#00E5FF"), // Bright Neon Cyan
                Color.parseColor("#00838F")
        };
        float[] positions = new float[]{0.0f, 0.45f, 0.75f, 1.0f};

        SweepGradient gradient = new SweepGradient(cx, cy, colors, positions);
        // Rotate gradient matrix to align with start angle
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setRotate(START_ANGLE - 10f, cx, cy);
        gradient.setLocalMatrix(matrix);

        progressPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Draw track
        canvas.drawArc(arcBounds, START_ANGLE, SWEEP_MAX_ANGLE, false, trackPaint);

        // Draw glow & progress
        float sweepAngle = (animatedProgress / 100f) * SWEEP_MAX_ANGLE;
        if (sweepAngle > 0) {
            canvas.drawArc(arcBounds, START_ANGLE, sweepAngle, false, glowPaint);
            canvas.drawArc(arcBounds, START_ANGLE, sweepAngle, false, progressPaint);
        }
    }
}
