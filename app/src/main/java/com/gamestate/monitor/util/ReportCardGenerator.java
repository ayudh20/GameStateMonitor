package com.gamestate.monitor.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.gamestate.monitor.model.GameSession;
import com.gamestate.monitor.model.SessionInsight;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ReportCardGenerator
 * -------------------
 * Synthesizes a high-definition, AMOLED dark theme shareable gaming report card image (PNG)
 * with neon cyan accents, stylized grade badges, telemetry grid, and key insights.
 */
public class ReportCardGenerator {

    private static final String TAG = "ReportCardGenerator";

    public static Bitmap generateReportCardBitmap(Context context, GameSession session, Drawable gameIcon) {
        if (session == null) return null;

        int width = 1080;
        int height = 1350;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. AMOLED Dark Background
        canvas.drawColor(Color.parseColor("#050508"));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 2. Neon Cyan Accent Glow & Card Frame
        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setColor(Color.parseColor("#1C2834"));

        RectF outerCard = new RectF(40, 40, width - 40, height - 40);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#0C0E14"));
        canvas.drawRoundRect(outerCard, 36, 36, paint);
        canvas.drawRoundRect(outerCard, 36, 36, borderPaint);

        // Subtle Cyan Gradient accent on top edge
        Paint topGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        LinearGradient topGlow = new LinearGradient(
                outerCard.left, outerCard.top, outerCard.right, outerCard.top + 16,
                Color.parseColor("#00E5FF"), Color.parseColor("#00838F"), Shader.TileMode.CLAMP);
        topGlowPaint.setShader(topGlow);
        topGlowPaint.setStyle(Paint.Style.FILL);
        RectF glowBar = new RectF(outerCard.left + 36, outerCard.top, outerCard.right - 36, outerCard.top + 6);
        canvas.drawRoundRect(glowBar, 3, 3, topGlowPaint);

        // 3. Header: App Branding
        paint.setColor(Color.parseColor("#00E5FF"));
        paint.setTextSize(26);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setLetterSpacing(0.12f);
        canvas.drawText("GAMESTATE MONITOR // BENCHMARK SUITE", 80, 110, paint);

        paint.setLetterSpacing(0.0f);
        paint.setColor(Color.parseColor("#8A8A98"));
        paint.setTextSize(20);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault());
        String dateStr = sdf.format(new Date(session.getStartTimeMs()));
        canvas.drawText(dateStr, 80, 145, paint);

        // 4. Game Info & Icon
        float iconTop = 190;
        float iconSize = 100;
        if (gameIcon != null) {
            gameIcon.setBounds(80, (int) iconTop, (int) (80 + iconSize), (int) (iconTop + iconSize));
            gameIcon.draw(canvas);
        } else {
            Paint iconBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconBg.setColor(Color.parseColor("#151822"));
            canvas.drawRoundRect(new RectF(80, iconTop, 80 + iconSize, iconTop + iconSize), 24, 24, iconBg);
        }

        // Game Name
        paint.setColor(Color.WHITE);
        paint.setTextSize(44);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        String gameTitle = session.getAppName() != null ? session.getAppName() : "Mobile Game";
        if (gameTitle.length() > 20) gameTitle = gameTitle.substring(0, 18) + "...";
        canvas.drawText(gameTitle, 205, iconTop + 55, paint);

        // Duration Pill
        paint.setColor(Color.parseColor("#8A8A98"));
        paint.setTextSize(22);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        canvas.drawText("Session Duration: " + FormatUtils.formatDuration(session.getDurationMs()), 205, iconTop + 90, paint);

        // 5. Stylized Grade Badge (Right aligned)
        float gradeRight = width - 80;
        float gradeLeft = gradeRight - 130;
        RectF gradeBox = new RectF(gradeLeft, 100, gradeRight, 240);

        Paint gradeBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradeBoxPaint.setColor(Color.parseColor("#091E24"));
        canvas.drawRoundRect(gradeBox, 28, 28, gradeBoxPaint);

        Paint gradeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradeBorderPaint.setStyle(Paint.Style.STROKE);
        gradeBorderPaint.setStrokeWidth(3f);
        gradeBorderPaint.setColor(Color.parseColor("#00E5FF"));
        canvas.drawRoundRect(gradeBox, 28, 28, gradeBorderPaint);

        Paint gradeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradeTextPaint.setColor(Color.parseColor("#00E5FF"));
        gradeTextPaint.setTextSize(64);
        gradeTextPaint.setTextAlign(Paint.Align.CENTER);
        gradeTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        String grade = session.getGrade() != null ? session.getGrade() : "A";
        canvas.drawText(grade, gradeBox.centerX(), gradeBox.centerY() + 18, gradeTextPaint);

        Paint gradeSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gradeSubPaint.setColor(Color.parseColor("#636370"));
        gradeSubPaint.setTextSize(14);
        gradeSubPaint.setTextAlign(Paint.Align.CENTER);
        gradeSubPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        canvas.drawText("GRADE", gradeBox.centerX(), gradeBox.bottom - 12, gradeSubPaint);

        // 6. Horizontal Divider
        Paint divPaint = new Paint();
        divPaint.setColor(Color.parseColor("#1A1D26"));
        divPaint.setStrokeWidth(2f);
        canvas.drawLine(80, 320, width - 80, 320, divPaint);

        // 7. Core Benchmark Metrics Grid (2x3 tiles)
        float gridTop = 350;
        float tileW = (width - 160 - 30) / 2f;
        float tileH = 130;
        float spacing = 20;

        drawMetricTile(canvas, 80, gridTop, tileW, tileH, "AVERAGE FPS", String.format(Locale.getDefault(), "%.1f", session.getAvgFps()), "FPS", Color.parseColor("#00E5FF"));
        drawMetricTile(canvas, 80 + tileW + spacing, gridTop, tileW, tileH, "1% LOW FPS", String.format(Locale.getDefault(), "%.1f", session.getOnePercentLowFps()), "FPS", Color.WHITE);

        drawMetricTile(canvas, 80, gridTop + tileH + spacing, tileW, tileH, "GAMEPLAY MIN", String.format(Locale.getDefault(), "%.1f", session.getGameplayMinFps()), "FPS", Color.WHITE);
        drawMetricTile(canvas, 80 + tileW + spacing, gridTop + tileH + spacing, tileW, tileH, "STABILITY SCORE", String.format(Locale.getDefault(), "%.0f%%", session.getStabilityScorePercent()), "Score", Color.parseColor("#00E676"));

        drawMetricTile(canvas, 80, gridTop + (tileH + spacing) * 2, tileW, tileH, "PEAK TEMPERATURE", String.format(Locale.getDefault(), "%.0f°C", session.getPeakTempC()), session.getThermalStatus(), Color.parseColor("#FFA726"));
        drawMetricTile(canvas, 80 + tileW + spacing, gridTop + (tileH + spacing) * 2, tileW, tileH, "BATTERY DRAIN", String.format(Locale.getDefault(), "%.1f%%/hr", session.getAvgDrainRatePerHour()), session.getBatteryConsumedPercent() + "% used", Color.parseColor("#80D8FF"));

        // 8. Session Insights Section
        float insightsTop = gridTop + (tileH + spacing) * 3 + 20;
        canvas.drawLine(80, insightsTop, width - 80, insightsTop, divPaint);

        paint.setColor(Color.WHITE);
        paint.setTextSize(26);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText("AI SESSION INSIGHTS", 80, insightsTop + 45, paint);

        List<SessionInsight> insights = SessionInsightsEngine.analyzeSession(session, null);
        float currentY = insightsTop + 90;
        int maxInsights = Math.min(3, insights.size());

        for (int i = 0; i < maxInsights; i++) {
            SessionInsight ins = insights.get(i);
            drawInsightRow(canvas, 80, currentY, width - 160, ins);
            currentY += 85;
        }

        // 9. Watermark Footer
        Paint footPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        footPaint.setColor(Color.parseColor("#5A5A6A"));
        footPaint.setTextSize(18);
        footPaint.setTextAlign(Paint.Align.CENTER);
        footPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        canvas.drawText("PURE HARDWARE TELEMETRY • ZERO FAKE DATA • GAMESTATE MONITOR", width / 2f, height - 70, footPaint);

        return bitmap;
    }

    private static void drawMetricTile(Canvas canvas, float x, float y, float w, float h, String label, String value, String unit, int valColor) {
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#10121A"));
        RectF tile = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(tile, 20, 20, bgPaint);

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setColor(Color.parseColor("#1B1F2A"));
        canvas.drawRoundRect(tile, 20, 20, strokePaint);

        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#7E8292"));
        labelPaint.setTextSize(18);
        labelPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        labelPaint.setLetterSpacing(0.06f);
        canvas.drawText(label, x + 20, y + 36, labelPaint);

        Paint valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        valPaint.setColor(valColor);
        valPaint.setTextSize(42);
        valPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(value, x + 20, y + 88, valPaint);

        Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        unitPaint.setColor(Color.parseColor("#636370"));
        unitPaint.setTextSize(18);
        unitPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        canvas.drawText(unit, x + 20, y + 115, unitPaint);
    }

    private static void drawInsightRow(Canvas canvas, float x, float y, float w, SessionInsight ins) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.parseColor("#10131B"));
        RectF rect = new RectF(x, y, x + w, y + 70);
        canvas.drawRoundRect(rect, 16, 16, bg);

        // Icon indicator
        String icon = "✓";
        int iconColor = Color.parseColor("#00E676");
        if (ins.getType() == SessionInsight.Type.WARNING) {
            icon = "⚠";
            iconColor = Color.parseColor("#FFA726");
        } else if (ins.getType() == SessionInsight.Type.COMPARISON) {
            icon = "📊";
            iconColor = Color.parseColor("#00E5FF");
        } else if (ins.getType() == SessionInsight.Type.MILESTONE) {
            icon = "🏆";
            iconColor = Color.parseColor("#BA68C8");
        }

        Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconPaint.setColor(iconColor);
        iconPaint.setTextSize(24);
        canvas.drawText(icon, x + 20, y + 42, iconPaint);

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.WHITE);
        titlePaint.setTextSize(20);
        titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        canvas.drawText(ins.getTitle(), x + 60, y + 32, titlePaint);

        Paint msgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        msgPaint.setColor(Color.parseColor("#8A8A98"));
        msgPaint.setTextSize(17);
        msgPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        String msg = ins.getMessage();
        if (msg.length() > 65) msg = msg.substring(0, 62) + "...";
        canvas.drawText(msg, x + 60, y + 55, msgPaint);
    }

    /**
     * Saves the bitmap to internal cache and returns a shareable content Uri.
     */
    public static Uri saveReportCardToCache(Context context, Bitmap bitmap) {
        try {
            File cacheDir = new File(context.getCacheDir(), "reports");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            File file = new File(cacheDir, "benchmark_report_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }

            return FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    file
            );
        } catch (Exception e) {
            Log.e(TAG, "Error saving report card to cache: " + e.getMessage());
            return null;
        }
    }

    public static void shareReportCard(Context context, GameSession session, Drawable gameIcon) {
        Bitmap bmp = generateReportCardBitmap(context, session, gameIcon);
        if (bmp == null) return;

        Uri uri = saveReportCardToCache(context, bmp);
        if (uri == null) return;

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.putExtra(Intent.EXTRA_SUBJECT, "GameState Monitor - " + session.getAppName() + " Benchmark");
        intent.putExtra(Intent.EXTRA_TEXT, "🎮 " + session.getAppName() + " Benchmark Report\nAvg FPS: " +
                String.format(Locale.getDefault(), "%.1f", session.getAvgFps()) + " | Grade: " + session.getGrade() + "\nGenerated by GameState Monitor");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        context.startActivity(Intent.createChooser(intent, "Share Benchmark Report Card"));
    }
}
