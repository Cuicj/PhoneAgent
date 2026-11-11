package com.example.baseapp.ui.sensor;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.baseapp.data.sensor.AltitudeMath;
import com.example.baseapp.databinding.FragmentAltitudeBinding;

/**
 * 海拔页：优先气压计，缺失时退回 GPS 高度，两者都不可用时明确说明。
 */
public class AltitudeFragment extends Fragment implements SensorEventListener {

    private static final long UI_INTERVAL_MS = 500L;

    private FragmentAltitudeBinding binding;
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private LocationManager locationManager;

    private boolean gpsMode;
    private boolean gpsRequested;
    private boolean permissionPermanentlyDenied;

    private boolean hasPressure;
    private float latestPressure = Float.NaN;
    private float baselinePressure = Float.NaN;

    private boolean gpsFixed;
    private boolean gpsNoAltitude;
    private double gpsAltitude = Double.NaN;
    private float gpsAccuracy = Float.NaN;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable uiTask = new Runnable() {
        @Override
        public void run() {
            updatePressureUi();
            handler.postDelayed(this, UI_INTERVAL_MS);
        }
    };

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding == null) {
                    return;
                }
                if (granted) {
                    permissionPermanentlyDenied = false;
                    binding.permissionPanel.setVisibility(View.GONE);
                    startGps();
                } else {
                    permissionPermanentlyDenied = !shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_FINE_LOCATION);
                    showPermissionPanel();
                }
            });

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            handleLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            // 旧系统回调，无需处理
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
            if (binding != null) {
                binding.gpsHint.setText(R.string.sensor_altitude_gps_desc);
            }
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
            if (binding != null) {
                binding.gpsHint.setText(R.string.sensor_altitude_location_disabled);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAltitudeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sensorManager = (SensorManager) requireContext()
                .getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        pressureSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);

        binding.resetBaseline.setOnClickListener(button -> {
            if (!Float.isNaN(latestPressure)) {
                baselinePressure = latestPressure;
                updatePressureUi();
            }
        });

        if (pressureSensor != null) {
            setupPressureMode();
        } else if (hasGpsCapability()) {
            setupGpsMode();
        } else {
            setupUnavailableMode();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor_altitude);
        if (pressureSensor != null && sensorManager != null) {
            sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL);
            handler.post(uiTask);
        } else if (gpsMode && hasLocationPermission()) {
            startGps();
        }
    }

    @Override
    public void onPause() {
        // 页面不可见时注销气压计与定位监听
        handler.removeCallbacks(uiTask);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        stopGps();
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
        if (event.sensor.getType() != Sensor.TYPE_PRESSURE) {
            return;
        }
        latestPressure = event.values[0];
        hasPressure = true;
        if (Float.isNaN(baselinePressure)) {
            baselinePressure = latestPressure;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 气压计精度不影响展示
    }

    private void setupPressureMode() {
        gpsMode = false;
        binding.modeStatus.setText(R.string.sensor_altitude_pressure_mode);
        binding.modeDesc.setText(R.string.sensor_altitude_pressure_desc);
        binding.pressureGroup.setVisibility(View.VISIBLE);
        binding.gpsGroup.setVisibility(View.GONE);
        binding.unavailableGroup.setVisibility(View.GONE);
    }

    private void setupGpsMode() {
        gpsMode = true;
        binding.modeStatus.setText(R.string.sensor_altitude_gps_mode);
        binding.modeDesc.setText(R.string.sensor_altitude_gps_desc);
        binding.pressureGroup.setVisibility(View.GONE);
        binding.gpsGroup.setVisibility(View.VISIBLE);
        binding.unavailableGroup.setVisibility(View.GONE);
        binding.grantButton.setOnClickListener(button -> {
            if (permissionPermanentlyDenied) {
                openAppSettings();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });
        if (hasLocationPermission()) {
            binding.permissionPanel.setVisibility(View.GONE);
            startGps();
        } else {
            showPermissionPanel();
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void setupUnavailableMode() {
        gpsMode = false;
        binding.modeStatus.setText(R.string.sensor_altitude_none_title);
        binding.modeDesc.setText(R.string.sensor_altitude_gps_unavailable);
        binding.pressureGroup.setVisibility(View.GONE);
        binding.gpsGroup.setVisibility(View.GONE);
        binding.unavailableGroup.setVisibility(View.VISIBLE);
        binding.unavailableDesc.setText(R.string.sensor_altitude_none_desc);
    }

    /** 刷新气压、气压高度与相对高度 */
    private void updatePressureUi() {
        if (binding == null || !hasPressure) {
            return;
        }
        double altitude = AltitudeMath.altitudeMeters(latestPressure);
        binding.pressureValue.setText(getString(R.string.sensor_altitude_pressure_value, latestPressure));
        binding.altitudeValue.setText(getString(R.string.sensor_altitude_value, altitude));
        if (!Float.isNaN(baselinePressure)) {
            double baselineAltitude = AltitudeMath.altitudeMeters(baselinePressure);
            binding.relativeValue.setText(getString(R.string.sensor_altitude_relative,
                    altitude - baselineAltitude));
            binding.resetBaseline.setEnabled(true);
        }
    }

    private boolean hasGpsCapability() {
        if (requireContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
            return true;
        }
        return locationManager != null
                && locationManager.getAllProviders().contains(LocationManager.GPS_PROVIDER);
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionPanel() {
        binding.permissionPanel.setVisibility(View.VISIBLE);
        binding.permissionDesc.setText(permissionPermanentlyDenied
                ? R.string.sensor_altitude_permission_denied
                : R.string.sensor_altitude_permission_desc);
        binding.grantButton.setText(permissionPermanentlyDenied
                ? R.string.wireless_permission_settings : R.string.sensor_grant_permission);
    }

    private void startGps() {
        if (!gpsMode || locationManager == null || !hasLocationPermission() || gpsRequested) {
            return;
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            binding.gpsHint.setText(R.string.sensor_altitude_location_disabled);
        }
        try {
            Location last = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) {
                handleLocation(last);
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    2000L, 1f, locationListener, Looper.getMainLooper());
            gpsRequested = true;
        } catch (SecurityException e) {
            showPermissionPanel();
        } catch (IllegalArgumentException e) {
            binding.gpsValue.setText(R.string.sensor_altitude_gps_unavailable);
        }
    }

    private void stopGps() {
        if (!gpsRequested || locationManager == null) {
            return;
        }
        try {
            locationManager.removeUpdates(locationListener);
        } catch (SecurityException ignored) {
            // 权限被撤销时忽略
        }
        gpsRequested = false;
    }

    private void handleLocation(Location location) {
        if (binding == null) {
            return;
        }
        if (location.hasAltitude()) {
            gpsAltitude = location.getAltitude();
            gpsFixed = true;
            gpsNoAltitude = false;
        } else {
            gpsFixed = false;
            gpsNoAltitude = true;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            gpsAccuracy = location.getVerticalAccuracyMeters();
        }
        updateGpsUi();
    }

    private void updateGpsUi() {
        if (binding == null || !gpsMode) {
            return;
        }
        if (gpsFixed) {
            binding.gpsValue.setText(getString(R.string.sensor_altitude_gps_value, gpsAltitude));
            binding.gpsAccuracy.setText(gpsAccuracy > 0f
                    ? getString(R.string.sensor_altitude_gps_accuracy, gpsAccuracy)
                    : getString(R.string.sensor_altitude_gps_accuracy_unknown));
        } else if (gpsNoAltitude) {
            binding.gpsValue.setText(R.string.sensor_altitude_gps_no_altitude);
            binding.gpsAccuracy.setText(R.string.sensor_altitude_gps_accuracy_unknown);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }
}
