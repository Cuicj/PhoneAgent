package com.example.baseapp.ui.home;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
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
import com.example.baseapp.data.IrCapability;
import com.example.baseapp.databinding.FragmentHomeBinding;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 首页入口：进入本机应用权限总览
        binding.appPermissionLink.setOnClickListener(link ->
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_navigation_home_to_appPermissionList));

        // 红外卡片：显式展示硬件结论，入口始终可点击
        binding.irCard.setOnClickListener(card -> openIrHome());
        binding.irLink.setOnClickListener(link -> openIrHome());

        // 相机卡片：进入独立的相机模块（暗光检测）
        binding.cameraCard.setOnClickListener(card -> openCameraHome());
        binding.cameraLink.setOnClickListener(link -> openCameraHome());

        // 无线安全检查卡片：WiFi 与蓝牙可疑设备检测
        binding.wirelessCard.setOnClickListener(card -> openWirelessHome());
        binding.wirelessLink.setOnClickListener(link -> openWirelessHome());

        // 传感器卡片：陀螺仪、指南针、海拔、分贝与自检清单
        binding.sensorCard.setOnClickListener(card -> openSensorHome());
        binding.sensorLink.setOnClickListener(link -> openSensorHome());

        // 设备信息卡片：品牌型号、芯片、电池、序列号与系统版本
        binding.deviceCard.setOnClickListener(card -> openDeviceInfo());
        binding.deviceLink.setOnClickListener(link -> openDeviceInfo());

        boolean irSupported = IrCapability.hasEmitter(requireContext());
        if (irSupported) {
            binding.irStatus.setText(R.string.ir_supported);
            binding.irStatus.setBackgroundResource(R.drawable.bg_badge_granted);
            binding.irStatus.setTextColor(requireContext().getColor(
                    R.color.brand_on_primary_container));
            binding.irReason.setText(R.string.ir_supported_desc);
        } else {
            binding.irStatus.setText(R.string.ir_unsupported);
            binding.irStatus.setBackgroundResource(R.drawable.bg_badge_normal);
            binding.irStatus.setTextColor(requireContext().getColor(
                    R.color.on_access_container));
            binding.irReason.setText(R.string.ir_unsupported_reason);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 返回首页时刷新传感器、麦克风与设备概要
        if (binding != null) {
            binding.sensorStatus.setText(buildSensorSummary());
            binding.deviceStatus.setText(getString(R.string.home_device_status,
                    Build.MODEL, Build.VERSION.RELEASE));
        }
    }

    private void openIrHome() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_home_to_irHome);
    }

    private void openCameraHome() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_home_to_cameraHome);
    }

    private void openWirelessHome() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_home_to_wirelessHome);
    }

    private void openSensorHome() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_home_to_sensorHome);
    }

    private void openDeviceInfo() {
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_home_to_deviceInfo);
    }

    /** 汇总传感器与麦克风状态，供首页卡片展示 */
    private String buildSensorSummary() {
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

        String available = getString(R.string.sensor_state_available);
        String unavailable = getString(R.string.sensor_state_unavailable);
        String compass = accel && mag ? available : unavailable;
        String altitude = barometer ? getString(R.string.sensor_state_barometer)
                : gps ? getString(R.string.sensor_state_gps_only)
                : getString(R.string.sensor_state_no_altitude);
        String sound;
        if (!microphone) {
            sound = getString(R.string.sensor_state_sound_none);
        } else if (ContextCompat.checkSelfPermission(context,
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            sound = getString(R.string.sensor_state_sound_ok);
        } else {
            sound = getString(R.string.sensor_state_sound_perm);
        }
        return getString(R.string.sensor_home_summary,
                gyro ? available : unavailable, compass, altitude, sound);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
