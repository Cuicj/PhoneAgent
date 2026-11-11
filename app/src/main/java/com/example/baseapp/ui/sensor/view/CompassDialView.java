package com.example.baseapp.ui.sensor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * 指南针刻度盘：表盘随方位角反向旋转，顶部固定指针表示设备朝向。
 */
public class CompassDialView extends View {

    private static final String[] CARDINALS = {"N", "E", "S", "W"};
    private static final int COLOR_RING = 0xFFE8EAED;
    private static final int COLOR_TICK = 0xFF9AA0A6;
    private static final int COLOR_TEXT = 0xFF3C4043;
    private static final int COLOR_NORTH = 0xFFD93025;
    private static final int COLOR_SOUTH = 0xFF9AA0A6;
    private static final int COLOR_ACCENT = 0xFF0B57D0;

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint majorTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint northPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint southPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float azimuth;

    public CompassDialView(Context context) {
        super(context);
        init();
    }

    public CompassDialView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CompassDialView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor(COLOR_RING);

        tickPaint.setStrokeWidth(dp(1.5f));
        tickPaint.setColor(COLOR_TICK);
        majorTickPaint.setStrokeWidth(dp(2.5f));
        majorTickPaint.setColor(COLOR_TEXT);

        textPaint.setColor(COLOR_TEXT);
        textPaint.setTextSize(dp(16));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);

        northPaint.setColor(COLOR_NORTH);
        southPaint.setColor(COLOR_SOUTH);
        hubPaint.setColor(COLOR_TEXT);
        markerPaint.setColor(COLOR_ACCENT);
    }

    /** 更新方位角（0–360） */
    public void setAzimuth(float azimuth) {
        this.azimuth = azimuth;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - dp(10);
        if (radius <= 0) {
            return;
        }

        canvas.drawCircle(cx, cy, radius, ringPaint);

        // 表盘随方位角反向旋转
        canvas.save();
        canvas.rotate(-azimuth, cx, cy);

        for (int degree = 0; degree < 360; degree += 15) {
            canvas.save();
            canvas.rotate(degree, cx, cy);
            boolean major = degree % 90 == 0;
            boolean medium = degree % 45 == 0;
            float length = major ? dp(14) : medium ? dp(10) : dp(6);
            float start = cy - radius + dp(6);
            canvas.drawLine(cx, start, cx, start + length,
                    major ? majorTickPaint : tickPaint);
            canvas.restore();
        }

        for (int i = 0; i < CARDINALS.length; i++) {
            canvas.save();
            canvas.rotate(i * 90f, cx, cy);
            canvas.drawText(CARDINALS[i], cx, cy - radius + dp(44), textPaint);
            canvas.restore();
        }

        // 指针：红色指向北，灰色指向南
        path.reset();
        path.moveTo(cx, cy - radius + dp(64));
        path.lineTo(cx - dp(7), cy);
        path.lineTo(cx + dp(7), cy);
        path.close();
        canvas.drawPath(path, northPaint);

        path.reset();
        path.moveTo(cx, cy + radius - dp(64));
        path.lineTo(cx - dp(7), cy);
        path.lineTo(cx + dp(7), cy);
        path.close();
        canvas.drawPath(path, southPaint);

        canvas.restore();

        // 顶部固定标记：表示设备正前方
        path.reset();
        path.moveTo(cx, cy - radius + dp(2));
        path.lineTo(cx - dp(7), cy - radius - dp(7));
        path.lineTo(cx + dp(7), cy - radius - dp(7));
        path.close();
        canvas.drawPath(path, markerPaint);

        canvas.drawCircle(cx, cy, dp(5), hubPaint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
