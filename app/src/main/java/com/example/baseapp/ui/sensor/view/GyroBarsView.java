package com.example.baseapp.ui.sensor.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 陀螺仪三轴数值条：以中线为 0，向左/右表示负/正角速度。
 */
public class GyroBarsView extends View {

    /** 数值条量程（rad/s） */
    private static final float RANGE = 5f;
    private static final String[] LABELS = {"X", "Y", "Z"};
    private static final int[] COLORS = {0xFF0B57D0, 0xFFE8710A, 0xFF7B1FA2};

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float[] values = new float[3];

    public GyroBarsView(Context context) {
        super(context);
        init();
    }

    public GyroBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GyroBarsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        trackPaint.setColor(0xFFE8EAED);
        labelPaint.setColor(0xFF3C4043);
        labelPaint.setTextSize(dp(13));
        centerPaint.setColor(0xFF9AA0A6);
        centerPaint.setStrokeWidth(dp(1));
    }

    /** 更新三轴角速度（rad/s） */
    public void setValues(float x, float y, float z) {
        values[0] = x;
        values[1] = y;
        values[2] = z;
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
        float labelWidth = dp(26);
        float right = width - dp(2);
        float rowHeight = height / 3f;
        float barHeight = dp(12);
        float radius = barHeight / 2f;
        float center = labelWidth + (right - labelWidth) / 2f;

        for (int i = 0; i < 3; i++) {
            float cy = rowHeight * i + rowHeight / 2f;
            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(LABELS[i], 0, cy + labelPaint.getTextSize() / 3f, labelPaint);

            // 整条轨道
            rect.set(labelWidth, cy - barHeight / 2f, right, cy + barHeight / 2f);
            canvas.drawRoundRect(rect, radius, radius, trackPaint);
            // 0 刻度
            canvas.drawLine(center, cy - barHeight, center, cy + barHeight, centerPaint);

            float ratio = Math.max(-1f, Math.min(1f, values[i] / RANGE));
            float extent = ratio * (right - labelWidth) / 2f;
            barPaint.setColor(COLORS[i]);
            if (extent >= 0) {
                rect.set(center, cy - barHeight / 2f, center + extent, cy + barHeight / 2f);
            } else {
                rect.set(center + extent, cy - barHeight / 2f, center, cy + barHeight / 2f);
            }
            if (rect.width() > 0) {
                canvas.drawRoundRect(rect, radius, radius, barPaint);
            }
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
