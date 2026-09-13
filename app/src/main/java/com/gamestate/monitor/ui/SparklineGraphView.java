package com.gamestate.monitor.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SparklineGraphView
 * ------------------
 * Lightweight real-time waveform sparkline rendering smooth Bézier curves
 * with a glowing stroke and translucent gradient fill.
 */
public class SparklineGraphView extends View {

    private Paint linePaint;
    private Paint fillPaint;
    private Path linePath;
    private Path fillPath;

    private int lineColor = Color.parseColor("#00D2E0");
    private final List<Float> dataPoints = new ArrayList<>();
    private static final int MAX_POINTS = 16;

    public SparklineGraphView(Context context) {
        super(context);
        init();
    }

    public SparklineGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SparklineGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(lineColor);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        linePath = new Path();
        fillPath = new Path();

        // Seed with attractive initial wave
        seedInitialWave();
    }

    private void seedInitialWave() {
        dataPoints.clear();
        float[] seed = new float[]{20f, 25f, 18f, 35f, 40f, 28f, 50f, 45f, 60f, 55f, 70f, 65f, 58f, 62f, 50f, 55f};
        for (float v : seed) {
            dataPoints.add(v);
        }
    }

    public void setLineColor(int color) {
        this.lineColor = color;
        linePaint.setColor(lineColor);
        updateGradient(getWidth(), getHeight());
        invalidate();
    }

    public void addPoint(float value) {
        if (dataPoints.size() >= MAX_POINTS) {
            dataPoints.remove(0);
        }
        dataPoints.add(value);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateGradient(w, h);
    }

    private void updateGradient(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int topColor = Color.argb(60, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor));
        int bottomColor = Color.argb(0, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor));
        LinearGradient gradient = new LinearGradient(0, 0, 0, h, topColor, bottomColor, Shader.TileMode.CLAMP);
        fillPaint.setShader(gradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (dataPoints.size() < 2 || getWidth() <= 0 || getHeight() <= 0) return;

        float width = getWidth();
        float height = getHeight();
        float stepX = width / (dataPoints.size() - 1);

        linePath.reset();
        fillPath.reset();

        float minVal = 0f;
        float maxVal = 100f;

        float firstX = 0f;
        float firstY = height - ((dataPoints.get(0) - minVal) / (maxVal - minVal)) * (height * 0.8f) - (height * 0.1f);
        linePath.moveTo(firstX, firstY);
        fillPath.moveTo(firstX, height);
        fillPath.lineTo(firstX, firstY);

        for (int i = 1; i < dataPoints.size(); i++) {
            float prevX = (i - 1) * stepX;
            float prevY = height - ((dataPoints.get(i - 1) - minVal) / (maxVal - minVal)) * (height * 0.8f) - (height * 0.1f);

            float currentX = i * stepX;
            float currentY = height - ((dataPoints.get(i) - minVal) / (maxVal - minVal)) * (height * 0.8f) - (height * 0.1f);

            float controlX1 = prevX + (currentX - prevX) / 2f;
            float controlY1 = prevY;
            float controlX2 = prevX + (currentX - prevX) / 2f;
            float controlY2 = currentY;

            linePath.cubicTo(controlX1, controlY1, controlX2, controlY2, currentX, currentY);
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, currentX, currentY);
        }

        fillPath.lineTo(width, height);
        fillPath.close();

        // Draw soft filled area under curve
        canvas.drawPath(fillPath, fillPaint);

        // Draw glowing line
        canvas.drawPath(linePath, linePaint);
    }
}
