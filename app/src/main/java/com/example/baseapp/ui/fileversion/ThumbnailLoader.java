package com.example.baseapp.ui.fileversion;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轻量图片加载器：只解码已生成的缩略图，避免列表加载整张大图。
 */
public final class ThumbnailLoader {

    /** 列表缩略图解码上限（像素） */
    private static final int LIST_MAX_SIZE = 512;

    /** 大图预览解码上限（像素） */
    private static final int PREVIEW_MAX_SIZE = 1600;

    /** 缩略图内存缓存上限 */
    private static final int CACHE_BYTES = 16 * 1024 * 1024;

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private ThumbnailLoader() {
    }

    /**
     * 将缩略图加载到列表项。路径为空时清空图片。
     * 通过 View tag 判断复用，避免异步结果落到错误的位置。
     */
    public static void load(ImageView imageView, String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            imageView.setTag(null);
            imageView.setImageDrawable(null);
            return;
        }
        imageView.setTag(absolutePath);
        Bitmap cached = CACHE.get(absolutePath);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }
        imageView.setImageDrawable(null);
        EXECUTOR.execute(() -> {
            Bitmap bitmap = decodeSampled(absolutePath, LIST_MAX_SIZE);
            if (bitmap != null) {
                CACHE.put(absolutePath, bitmap);
            }
            MAIN_HANDLER.post(() -> {
                if (absolutePath.equals(imageView.getTag()) && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** 大图预览：不复用列表缓存，按屏幕需要解码 */
    public static void loadPreview(ImageView imageView, String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) {
            return;
        }
        EXECUTOR.execute(() -> {
            Bitmap bitmap = decodeSampled(absolutePath, PREVIEW_MAX_SIZE);
            if (bitmap != null) {
                MAIN_HANDLER.post(() -> imageView.setImageBitmap(bitmap));
            }
        });
    }

    private static Bitmap decodeSampled(String path, int maxSize) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxSize);
        return BitmapFactory.decodeFile(path, options);
    }

    private static int calculateInSampleSize(int width, int height, int maxSize) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxSize) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
