package com.example.baseapp.ui.apppermission;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用图标异步加载器：图标读取放在后台线程，内存缓存避免列表滚动时重复查询。
 */
public final class AppIconLoader {

    /** 图标内存缓存上限（按条目数），常用应用图标体积较小 */
    private static final int CACHE_SIZE = 160;

    private static final LruCache<String, Drawable> CACHE = new LruCache<>(CACHE_SIZE);

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private AppIconLoader() {
    }

    /**
     * 将应用图标加载到目标 ImageView。
     * 通过 View tag 判断复用，避免异步结果落到错误的列表项上。
     */
    public static void load(ImageView imageView, String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            imageView.setTag(null);
            imageView.setImageResource(android.R.drawable.sym_def_app_icon);
            return;
        }
        imageView.setTag(packageName);
        Drawable cached = CACHE.get(packageName);
        if (cached != null) {
            imageView.setImageDrawable(cached);
            return;
        }
        imageView.setImageResource(android.R.drawable.sym_def_app_icon);
        PackageManager pm = imageView.getContext().getApplicationContext().getPackageManager();
        EXECUTOR.execute(() -> {
            Drawable drawable = null;
            try {
                drawable = pm.getApplicationIcon(packageName);
            } catch (PackageManager.NameNotFoundException ignored) {
                // 应用可能已被卸载，回退到系统默认图标
            }
            if (drawable != null) {
                CACHE.put(packageName, drawable);
            }
            Drawable result = drawable;
            MAIN_HANDLER.post(() -> {
                if (packageName.equals(imageView.getTag()) && result != null) {
                    imageView.setImageDrawable(result);
                }
            });
        });
    }
}
