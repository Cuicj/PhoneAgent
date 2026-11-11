package com.example.baseapp.ui.sensor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.sensor.DecibelMath;
import com.example.baseapp.databinding.FragmentSoundLevelBinding;

/**
 * 分贝页：AudioRecord 采集 PCM，计算 RMS 并换算为相对声压级。
 * 数值未经校准，仅用于相对比较。
 */
public class SoundLevelFragment extends Fragment {

    private static final int SAMPLE_RATE = 44100;
    private static final long UI_INTERVAL_MS = 100L;

    private FragmentSoundLevelBinding binding;
    private AudioRecord audioRecord;
    private Thread worker;
    private volatile boolean recording;
    private volatile boolean resetRequested;
    private volatile double latestDb = Double.NaN;
    private volatile double peakDb = Double.NaN;
    private volatile double meanDb = Double.NaN;
    private volatile long blockCount;

    private boolean micAvailable;
    private boolean permissionPermanentlyDenied;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uiTask = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, UI_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding == null) {
                    return;
                }
                if (granted) {
                    permissionPermanentlyDenied = false;
                    binding.permissionPanel.setVisibility(View.GONE);
                    startRecording();
                } else {
                    permissionPermanentlyDenied = !shouldShowRequestPermissionRationale(
                            Manifest.permission.RECORD_AUDIO);
                    showPermissionPanel();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSoundLevelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        micAvailable = requireContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_MICROPHONE);

        binding.grantButton.setOnClickListener(button -> {
            if (permissionPermanentlyDenied) {
                openAppSettings();
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            }
        });
        binding.resetStats.setOnClickListener(button -> resetStats());

        if (!micAvailable) {
            binding.contentGroup.setVisibility(View.GONE);
            binding.unavailableCard.setVisibility(View.VISIBLE);
            return;
        }
        binding.contentGroup.setVisibility(View.VISIBLE);
        binding.unavailableCard.setVisibility(View.GONE);

        if (hasAudioPermission()) {
            binding.permissionPanel.setVisibility(View.GONE);
            startRecording();
        } else {
            showPermissionPanel();
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor_sound);
        handler.post(uiTask);
        if (micAvailable && hasAudioPermission() && audioRecord == null) {
            startRecording();
        }
    }

    @Override
    public void onPause() {
        // 页面不可见时停止采集并释放 AudioRecord
        handler.removeCallbacks(uiTask);
        stopRecording();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionPanel() {
        binding.permissionPanel.setVisibility(View.VISIBLE);
        binding.permissionDesc.setText(permissionPermanentlyDenied
                ? R.string.sensor_sound_permission_denied
                : R.string.sensor_sound_permission_desc);
        binding.grantButton.setText(permissionPermanentlyDenied
                ? R.string.wireless_permission_settings : R.string.sensor_grant_permission);
        binding.statusHint.setText("");
    }

    private void startRecording() {
        if (!micAvailable || !hasAudioPermission() || audioRecord != null) {
            return;
        }
        binding.statusHint.setText(R.string.sensor_sound_starting);
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minBuffer <= 0) {
            binding.statusHint.setText(getString(R.string.sensor_sound_error,
                    "设备不支持 44.1 kHz 单声道录音"));
            return;
        }
        AudioRecord record;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minBuffer * 2, 8192));
        } catch (SecurityException e) {
            showPermissionPanel();
            return;
        } catch (IllegalArgumentException e) {
            binding.statusHint.setText(getString(R.string.sensor_sound_error,
                    e.getMessage() == null ? "参数不被支持" : e.getMessage()));
            return;
        }
        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            record.release();
            binding.statusHint.setText(getString(R.string.sensor_sound_error, "初始化失败"));
            return;
        }
        audioRecord = record;
        recording = true;
        resetRequested = false;
        latestDb = Double.NaN;
        peakDb = Double.NaN;
        meanDb = Double.NaN;
        blockCount = 0;
        record.startRecording();
        worker = new Thread(() -> readLoop(record), "sensor-decibel");
        worker.start();
        binding.statusHint.setText(R.string.sensor_sound_running);
    }

    /** 后台线程读取 PCM 并计算分贝，UI 只读取最新值 */
    private void readLoop(AudioRecord record) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        short[] buffer = new short[2048];
        double sumRms = 0d;
        long count = 0L;
        double peak = 0d;
        while (recording) {
            int read;
            try {
                read = record.read(buffer, 0, buffer.length);
            } catch (IllegalStateException e) {
                break;
            }
            if (read <= 0) {
                if (read < 0) {
                    break;
                }
                continue;
            }
            if (resetRequested) {
                resetRequested = false;
                sumRms = 0d;
                count = 0L;
                peak = 0d;
            }
            double rms = DecibelMath.rms(buffer, read);
            double db = DecibelMath.rmsToDb(rms);
            sumRms += rms;
            count++;
            peak = Math.max(peak, db);
            latestDb = db;
            peakDb = peak;
            meanDb = DecibelMath.rmsToDb(sumRms / count);
            blockCount = count;
        }
    }

    private void stopRecording() {
        recording = false;
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record != null) {
            try {
                if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop();
                }
            } catch (IllegalStateException ignored) {
                // 已停止时忽略
            }
        }
        Thread thread = worker;
        worker = null;
        if (thread != null) {
            try {
                thread.join(300L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (record != null) {
            record.release();
        }
    }

    private void resetStats() {
        resetRequested = true;
        latestDb = Double.NaN;
        peakDb = Double.NaN;
        meanDb = Double.NaN;
        blockCount = 0L;
        updateUi();
    }

    private void updateUi() {
        if (binding == null) {
            return;
        }
        double db = latestDb;
        if (Double.isNaN(db)) {
            binding.soundValue.setText(R.string.sensor_sound_value_idle);
            binding.statsText.setText(R.string.sensor_sound_stats_idle);
            binding.levelMeter.setDecibel(Float.NaN);
        } else {
            binding.soundValue.setText(getString(R.string.sensor_sound_value, db));
            double peak = Double.isNaN(peakDb) ? db : peakDb;
            double mean = Double.isNaN(meanDb) ? db : meanDb;
            binding.statsText.setText(getString(R.string.sensor_sound_stats, peak, mean));
            binding.levelMeter.setDecibel((float) db);
        }
        long count = blockCount;
        binding.sampleCount.setText(count > 0
                ? getString(R.string.sensor_sound_samples, count) : "");
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }
}
