package com.example.baseapp.ui.sensor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 分贝强度条：分段显示 20–100 dB 的相对强度。
 */
public class LevelMeterView extends View {

    private static final int SEGMENTS = 24;
    private static final float MIN_DB = 20f;
    private static final float MAX_DB = 100f;
    private static final int COLOR_BG = 0xFFE8EAED;
    private static final int COLOR_LOW = 0xFF0B57D0;
    private static final int COLOR_MID = 0xFFE8710A;
    private static final int COLOR_HIGH = 0xFFD93025;

    private final Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float decibel = Float.NaN;

    public LevelMeterView(Context context) {
        super(context);
    }

    public LevelMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LevelMeterView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** 更新当前分贝值，NaN 表示无数据 */
    public void setDecibel(float decibel) {
        this.decibel = decibel;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        float gap = dp(3);
        float segmentWidth = (width - gap * (SEGMENTS - 1)) / SEGMENTS;
        float radius = dp(3);
        float ratio = Float.isNaN(decibel) ? 0f
                : Math.max(0f, Math.min(1f, (decibel - MIN_DB) / (MAX_DB - MIN_DB)));
        int filled = Math.round(ratio * SEGMENTS);

        for (int i = 0; i < SEGMENTS; i++) {
            float left = i * (segmentWidth + gap);
            rect.set(left, 0, left + segmentWidth, height);
            if (i < filled) {
                segmentPaint.setColor(segmentColor(i));
            } else {
                segmentPaint.setColor(COLOR_BG);
            }
            canvas.drawRoundRect(rect, radius, radius, segmentPaint);
        }
    }

    private int segmentColor(int index) {
        if (index < SEGMENTS * 0.6f) {
            return COLOR_LOW;
        }
        if (index < SEGMENTS * 0.85f) {
            return COLOR_MID;
        }
        return COLOR_HIGH;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
