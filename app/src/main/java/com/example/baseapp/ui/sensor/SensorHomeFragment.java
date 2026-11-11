package com.example.baseapp.ui.sensor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.baseapp.R;
import com.example.baseapp.databinding.FragmentSensorHomeBinding;

/**
 * 传感器总览：四个功能入口与自检清单。
 */
public class SensorHomeFragment extends Fragment {

    private FragmentSensorHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSensorHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.gyroCard.setOnClickListener(item -> open(R.id.action_sensorHome_to_gyroscope));
        binding.gyroEntry.setOnClickListener(item -> open(R.id.action_sensorHome_to_gyroscope));
        binding.compassCard.setOnClickListener(item -> open(R.id.action_sensorHome_to_compass));
        binding.compassEntry.setOnClickListener(item -> open(R.id.action_sensorHome_to_compass));
        binding.altitudeCard.setOnClickListener(item -> open(R.id.action_sensorHome_to_altitude));
        binding.altitudeEntry.setOnClickListener(item -> open(R.id.action_sensorHome_to_altitude));
        binding.soundCard.setOnClickListener(item -> open(R.id.action_sensorHome_to_soundLevel));
        binding.soundEntry.setOnClickListener(item -> open(R.id.action_sensorHome_to_soundLevel));
        binding.checkEntry.setOnClickListener(item -> open(R.id.action_sensorHome_to_sensorCheck));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor);
        refreshStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void open(int actionId) {
        NavHostFragment.findNavController(this).navigate(actionId);
    }

    /** 刷新各入口的硬件与权限状态 */
    private void refreshStatus() {
        Context context = requireContext();
        SensorManager sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);

        boolean gyro = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null;
        boolean accel = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null;
        boolean mag = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
        boolean barometer = sensorManager != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null;
        boolean gps = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
        boolean microphone = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_MICROPHONE);

        binding.gyroStatus.setText(gyro
                ? getString(R.string.sensor_state_available)
                : getString(R.string.sensor_state_unavailable));

        if (accel && mag) {
            binding.compassStatus.setText(R.string.sensor_state_available);
        } else {
            String missing = !accel ? getString(R.string.sensor_name_accelerometer)
                    : getString(R.string.sensor_name_magnetometer);
            binding.compassStatus.setText(getString(R.string.sensor_state_unavailable)
                    + "（" + missing + "）");
        }

        if (barometer) {
            binding.altitudeStatus.setText(R.string.sensor_state_barometer);
        } else if (gps) {
            binding.altitudeStatus.setText(R.string.sensor_state_gps_only);
        } else {
            binding.altitudeStatus.setText(R.string.sensor_state_no_altitude);
        }

        if (!microphone) {
            binding.soundStatus.setText(R.string.sensor_state_sound_none);
        } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            binding.soundStatus.setText(R.string.sensor_state_sound_ok);
        } else {
            binding.soundStatus.setText(R.string.sensor_state_sound_perm);
        }
    }
}
