package com.example.baseapp;

import android.app.Application;

import com.example.baseapp.data.FileVersionRepository;

/**
 * 应用入口：启动时触发本地版本文件的自检清理。
 */
public class PhoneAgentApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FileVersionRepository.getInstance(this).cleanupOrphans();
    }
}
