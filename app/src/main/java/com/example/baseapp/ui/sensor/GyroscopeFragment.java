package com.example.baseapp.ui.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.sensor.SensorCatalog;
import com.example.baseapp.databinding.FragmentGyroscopeBinding;

/**
 * 陀螺仪页：实时三轴角速度、采样率与数值条。
 */
public class GyroscopeFragment extends Fragment implements SensorEventListener {

    /** 注册周期：20 ms，约 50 Hz */
    private static final int SAMPLE_PERIOD_US = 20000;
    /** UI 节流：每 100 ms 刷新一次，避免高频回调卡顿 */
    private static final long UI_INTERVAL_MS = 100L;

    private FragmentGyroscopeBinding binding;
    private SensorManager sensorManager;
    private Sensor gyroSensor;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float[] values = new float[3];
    private boolean hasValue;
    private int eventCount;
    private long rateWindowStart;
    private float measuredRate;

    private final Runnable uiTask = new Runnable() {
        @Override
        public void run() {
            updateUi();
            handler.postDelayed(this, UI_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentGyroscopeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        gyroSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (gyroSensor == null) {
            binding.contentGroup.setVisibility(View.GONE);
            binding.unavailableCard.setVisibility(View.VISIBLE);
            return;
        }
        binding.contentGroup.setVisibility(View.VISIBLE);
        binding.unavailableCard.setVisibility(View.GONE);
        binding.gyroX.setText(getString(R.string.sensor_gyro_value_empty, "X"));
        binding.gyroY.setText(getString(R.string.sensor_gyro_value_empty, "Y"));
        binding.gyroZ.setText(getString(R.string.sensor_gyro_value_empty, "Z"));
        binding.sensorDetail.setText(getString(R.string.sensor_gyro_detail,
                gyroSensor.getVendor(),
                SensorCatalog.formatValue(gyroSensor.getMaximumRange()),
                SensorCatalog.formatValue(gyroSensor.getResolution())));
        float maxRate = SensorCatalog.maxSampleRate(gyroSensor);
        binding.maxRate.setText(maxRate > 0f
                ? getString(R.string.sensor_gyro_rate_max, maxRate) : "");
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor_gyro);
        if (gyroSensor == null || sensorManager == null) {
            return;
        }
        eventCount = 0;
        rateWindowStart = SystemClock.elapsedRealtime();
        measuredRate = 0f;
        sensorManager.registerListener(this, gyroSensor, SAMPLE_PERIOD_US);
        handler.post(uiTask);
    }

    @Override
    public void onPause() {
        // 页面不可见时立即注销，避免耗电与泄漏
        handler.removeCallbacks(uiTask);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        values[0] = event.values[0];
        values[1] = event.values[1];
        values[2] = event.values[2];
        hasValue = true;
        eventCount++;
        long now = SystemClock.elapsedRealtime();
        long elapsed = now - rateWindowStart;
        if (elapsed >= 500L) {
            measuredRate = eventCount * 1000f / elapsed;
            eventCount = 0;
            rateWindowStart = now;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 陀螺仪精度不影响读数展示
    }

    /** 主线程节流刷新数值与可视化 */
    private void updateUi() {
        if (binding == null) {
            return;
        }
        if (hasValue) {
            binding.gyroX.setText(getString(R.string.sensor_gyro_value,
                    "X", values[0], Math.toDegrees(values[0])));
            binding.gyroY.setText(getString(R.string.sensor_gyro_value,
                    "Y", values[1], Math.toDegrees(values[1])));
            binding.gyroZ.setText(getString(R.string.sensor_gyro_value,
                    "Z", values[2], Math.toDegrees(values[2])));
        }
        binding.gyroView.setValues(values[0], values[1], values[2]);
        int periodMs = SAMPLE_PERIOD_US / 1000;
        binding.sampleRate.setText(getString(R.string.sensor_gyro_rate,
                periodMs, 1000 / periodMs, measuredRate));
    }
}
