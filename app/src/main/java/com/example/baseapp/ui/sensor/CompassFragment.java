package com.example.baseapp.ui.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.sensor.CompassMath;
import com.example.baseapp.data.sensor.SensorCatalog;
import com.example.baseapp.databinding.FragmentCompassBinding;

/**
 * 指南针页：加速度计 + 磁力计计算方位角，展示刻度盘与校准提示。
 */
public class CompassFragment extends Fragment implements SensorEventListener {

    private static final long UI_INTERVAL_MS = 100L;
    private static final float SMOOTH_FACTOR = 0.2f;

    private FragmentCompassBinding binding;
    private SensorManager sensorManager;
    private Sensor accelSensor;
    private Sensor magSensor;

    private final float[] accel = new float[3];
    private final float[] mag = new float[3];
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientation = new float[3];
    private boolean hasAccel;
    private boolean hasMag;
    private int magAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;
    private float smoothedAzimuth = -1f;

    private final Handler handler = new Handler(Looper.getMainLooper());
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
        binding = FragmentCompassBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        accelSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelSensor == null || magSensor == null) {
            binding.contentGroup.setVisibility(View.GONE);
            binding.unavailableCard.setVisibility(View.VISIBLE);
            binding.unavailableDesc.setText(buildMissingReason());
        } else {
            binding.contentGroup.setVisibility(View.VISIBLE);
            binding.unavailableCard.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor_compass);
        if (accelSensor == null || magSensor == null || sensorManager == null) {
            return;
        }
        smoothedAzimuth = -1f;
        sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_UI);
        sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_UI);
        handler.post(uiTask);
    }

    @Override
    public void onPause() {
        // 页面不可见时注销两个传感器
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
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accel[0] = event.values[0];
            accel[1] = event.values[1];
            accel[2] = event.values[2];
            hasAccel = true;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            mag[0] = event.values[0];
            mag[1] = event.values[1];
            mag[2] = event.values[2];
            hasMag = true;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magAccuracy = accuracy;
        }
    }

    /** 主线程节流刷新方位角与刻度盘 */
    private void updateUi() {
        if (binding == null || !hasAccel || !hasMag) {
            return;
        }
        int axisX = SensorManager.AXIS_X;
        int axisY = SensorManager.AXIS_Y;
        switch (displayRotation()) {
            case Surface.ROTATION_90:
                axisX = SensorManager.AXIS_Y;
                axisY = SensorManager.AXIS_MINUS_X;
                break;
            case Surface.ROTATION_180:
                axisX = SensorManager.AXIS_MINUS_X;
                axisY = SensorManager.AXIS_MINUS_Y;
                break;
            case Surface.ROTATION_270:
                axisX = SensorManager.AXIS_MINUS_Y;
                axisY = SensorManager.AXIS_X;
                break;
            default:
                break;
        }
        float azimuth = CompassMath.azimuthDegrees(accel, mag, rotationMatrix,
                remappedMatrix, orientation, axisX, axisY);
        if (azimuth < 0f) {
            return;
        }
        smoothedAzimuth = CompassMath.smooth(smoothedAzimuth, azimuth, SMOOTH_FACTOR);
        binding.compassDial.setAzimuth(smoothedAzimuth);
        binding.azimuthText.setText(getString(R.string.sensor_compass_azimuth, smoothedAzimuth));
        String[] directions = getResources().getStringArray(R.array.sensor_compass_directions);
        binding.directionText.setText(directions[CompassMath.directionIndex(smoothedAzimuth)]);
        binding.accuracyText.setText(getString(R.string.sensor_compass_accuracy, accuracyLabel()));
        binding.interferenceWarning.setVisibility(isInterfered()
                ? View.VISIBLE : View.GONE);
    }

    private String buildMissingReason() {
        if (accelSensor == null && magSensor == null) {
            String missing = SensorCatalog.keyName(requireContext(), Sensor.TYPE_ACCELEROMETER)
                    + "、" + SensorCatalog.keyName(requireContext(), Sensor.TYPE_MAGNETIC_FIELD);
            return getString(R.string.sensor_compass_unavailable_both, missing);
        }
        return accelSensor == null
                ? getString(R.string.sensor_compass_unavailable_accel)
                : getString(R.string.sensor_compass_unavailable_mag);
    }

    private String accuracyLabel() {
        switch (magAccuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                return getString(R.string.sensor_compass_accuracy_high);
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                return getString(R.string.sensor_compass_accuracy_medium);
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                return getString(R.string.sensor_compass_accuracy_low);
            case SensorManager.SENSOR_STATUS_UNRELIABLE:
                return getString(R.string.sensor_compass_accuracy_unreliable);
            default:
                return getString(R.string.sensor_compass_accuracy_unknown);
        }
    }

    private boolean isInterfered() {
        return magAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE
                || magAccuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW;
    }

    @SuppressWarnings("deprecation")
    private int displayRotation() {
        return requireActivity().getWindowManager().getDefaultDisplay().getRotation();
    }
}
