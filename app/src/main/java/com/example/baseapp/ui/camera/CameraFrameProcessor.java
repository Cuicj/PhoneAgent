package com.example.baseapp.ui.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 相机帧处理器：在后台线程把 RGBA 帧旋转并按增益/对比度渲染，再回主线程显示。
 * 使用帧忙标记丢帧，避免处理堆积导致预览延迟。
 */
public class CameraFrameProcessor implements ImageAnalysis.Analyzer {

    private final ImageView target;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean frameBusy = new AtomicBoolean(false);

    private volatile float gain = 1.8f;
    private volatile float contrast = 1.1f;

    /** 复用的输出位图，避免每帧分配大对象 */
    private Bitmap output;

    public CameraFrameProcessor(ImageView target) {
        this.target = target;
    }

    public void setGain(float gain) {
        this.gain = gain;
    }

    public void setContrast(float contrast) {
        this.contrast = contrast;
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        // 上一帧还在渲染时直接丢弃当前帧
        if (!frameBusy.compareAndSet(false, true)) {
            image.close();
            return;
        }
        Bitmap processed = null;
        try {
            Bitmap source = image.toBitmap();
            int rotation = image.getImageInfo().getRotationDegrees();
            boolean swap = rotation == 90 || rotation == 270;
            int outWidth = swap ? source.getHeight() : source.getWidth();
            int outHeight = swap ? source.getWidth() : source.getHeight();
            if (output == null || output.getWidth() != outWidth
                    || output.getHeight() != outHeight) {
                output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(output);
            canvas.drawColor(Color.BLACK);
            canvas.save();
            canvas.rotate(rotation, outWidth / 2f, outHeight / 2f);
            Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(buildColorFilter());
            canvas.drawBitmap(source,
                    (outWidth - source.getWidth()) / 2f,
                    (outHeight - source.getHeight()) / 2f,
                    paint);
            canvas.restore();
            processed = output;
        } catch (RuntimeException ignored) {
            // 个别机型可能不支持 RGBA 输出，保持上一帧即可
        } finally {
            image.close();
        }

        Bitmap result = processed;
        if (result == null) {
            frameBusy.set(false);
            return;
        }
        mainHandler.post(() -> {
            if (target.isAttachedToWindow()) {
                target.setImageBitmap(result);
            }
            frameBusy.set(false);
        });
    }

    /** 亮度按增益放大、对比度围绕中灰调整，两者叠加用于找出暗处红外亮点 */
    private ColorMatrixColorFilter buildColorFilter() {
        float scale = gain * contrast;
        float translate = (1f - contrast) * 128f;
        ColorMatrix matrix = new ColorMatrix(new float[]{
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });
        return new ColorMatrixColorFilter(matrix);
    }
}
