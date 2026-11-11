package com.example.baseapp.ui.wireless;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.baseapp.R;
import com.example.baseapp.data.netcapture.CaptureState;
import com.example.baseapp.data.netcapture.CaptureStore;
import com.example.baseapp.data.wireless.WirelessPrefs;
import com.example.baseapp.databinding.FragmentWirelessHomeBinding;

/**
 * 无线安全检查总览：WiFi 与蓝牙的状态、最近扫描时间与入口。
 */
public class WirelessHomeFragment extends Fragment {

    private FragmentWirelessHomeBinding binding;

    /** 延迟刷新一次，等待抓取服务异步释放后再更新状态文案 */
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRefresh = this::refreshStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWirelessHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.wifiEntry.setOnClickListener(entry -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_wirelessHome_to_wifiScan));
        binding.btEntry.setOnClickListener(entry -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_wirelessHome_to_btScan));
        binding.captureEntry.setOnClickListener(entry -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_wirelessHome_to_networkCapture));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_wireless);
        refreshStatus();
        // 抓取服务的停止是异步的，稍后再刷新一次卡片状态
        statusHandler.postDelayed(statusRefresh, 800);
    }

    @Override
    public void onPause() {
        statusHandler.removeCallbacks(statusRefresh);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        statusHandler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }

    /** 刷新 WiFi/蓝牙开关状态、权限状态与最近扫描时间 */
    private void refreshStatus() {
        Context context = requireContext();

        boolean wifiSupported = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI);
        if (!wifiSupported) {
            binding.wifiStatus.setText(R.string.wireless_wifi_unsupported);
        } else if (!hasWifiPermission()) {
            // Android 13+ 未授权时读取开关状态会抛 SecurityException，这里直接展示未授权
            binding.wifiStatus.setText(getString(R.string.wireless_wifi_state_unknown)
                    + " · " + getString(R.string.wireless_permission_missing));
        } else {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            boolean enabled = wifiManager != null && wifiManager.isWifiEnabled();
            String state = getString(enabled
                    ? R.string.wireless_wifi_enabled : R.string.wireless_wifi_disabled_home);
            binding.wifiStatus.setText(state + " · "
                    + getString(R.string.wireless_permission_granted));
        }
        String wifiTime = WirelessPrefs.lastScanTime(context, WirelessPrefs.KEY_WIFI);
        binding.wifiLastScan.setText(wifiTime == null
                ? getString(R.string.wireless_last_scan_none)
                : getString(R.string.wireless_last_scan_time, wifiTime));

        BluetoothManager bluetoothManager = (BluetoothManager) context
                .getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bluetoothManager == null ? null : bluetoothManager.getAdapter();
        if (adapter == null) {
            binding.btStatus.setText(R.string.wireless_bt_unsupported_home);
        } else if (!hasBluetoothPermission()) {
            // Android 12+ 未授权时读取开关状态会抛 SecurityException，这里直接展示未授权
            binding.btStatus.setText(getString(R.string.wireless_bt_state_unknown)
                    + " · " + getString(R.string.wireless_permission_missing));
        } else {
            String state = getString(adapter.isEnabled()
                    ? R.string.wireless_bt_enabled : R.string.wireless_bt_disabled_home);
            binding.btStatus.setText(state + " · "
                    + getString(R.string.wireless_permission_granted));
        }
        String btTime = WirelessPrefs.lastScanTime(context, WirelessPrefs.KEY_BT);
        binding.btLastScan.setText(btTime == null
                ? getString(R.string.wireless_last_scan_none)
                : getString(R.string.wireless_last_scan_time, btTime));

        // 抓取模块状态：是否运行与已记录条数
        int captureCount = CaptureStore.get().count();
        binding.captureStatus.setText(getString(CaptureState.running
                ? R.string.wireless_capture_running
                : R.string.wireless_capture_stopped, captureCount));
    }

    private boolean hasWifiPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothPermission() {
        String permission = Build.VERSION.SDK_INT >= 31
                ? Manifest.permission.BLUETOOTH_SCAN
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
