package com.example.baseapp.ui.wireless;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.wireless.BtAnalyzer;
import com.example.baseapp.data.wireless.BtDevice;
import com.example.baseapp.data.wireless.OuiLookup;
import com.example.baseapp.data.wireless.RiskLevel;
import com.example.baseapp.data.wireless.WirelessPrefs;
import com.example.baseapp.databinding.FragmentBluetoothScanBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 蓝牙扫描页：经典蓝牙发现与 BLE 扫描并行，合并结果并按风险筛选。
 */
public class BluetoothScanFragment extends Fragment {

    private static final long SCAN_DURATION_MS = 12_000L;

    private FragmentBluetoothScanBinding binding;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bleScanner;
    private BtScanAdapter adapter;

    private final Map<String, BtDevice> devices = new LinkedHashMap<>();
    private RiskLevel filter;
    private boolean scanning;
    private boolean receiverRegistered;
    private boolean permissionPermanentlyDenied;
    private boolean refreshPosted;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::render;

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);
                if (device != null) {
                    onClassicFound(device, rssi);
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                scheduleRefresh();
            }
        }
    };

    private final ScanCallback bleCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (scanning) {
                onBleFound(result);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (!scanning) {
                return;
            }
            for (ScanResult result : results) {
                onBleFound(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            if (binding != null) {
                Toast.makeText(requireContext(), "BLE 扫描失败，错误码 " + errorCode,
                        Toast.LENGTH_SHORT).show();
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (binding == null) {
                    return;
                }
                if (hasBluetoothPermission()) {
                    permissionPermanentlyDenied = false;
                    binding.permissionPanel.setVisibility(View.GONE);
                    startScan();
                } else {
                    permissionPermanentlyDenied = !shouldShowRationale();
                    showPermissionPanel();
                    binding.grantButton.setText(permissionPermanentlyDenied
                            ? R.string.wireless_permission_settings : R.string.wireless_grant_permission);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBluetoothScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        BluetoothManager manager = (BluetoothManager) requireContext()
                .getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = manager == null ? null : manager.getAdapter();
        adapter = new BtScanAdapter(this::showDetail);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        binding.permissionPanel.setVisibility(View.GONE);
        binding.grantButton.setOnClickListener(button -> {
            if (permissionPermanentlyDenied) {
                openAppSettings();
            } else {
                requestPermission();
            }
        });
        binding.scanButton.setOnClickListener(button -> {
            if (hasBluetoothPermission()) {
                startScan();
            } else {
                requestPermission();
            }
        });
        binding.chipRiskAll.setOnClickListener(chip -> selectFilter(null));
        binding.chipRiskHigh.setOnClickListener(chip -> selectFilter(RiskLevel.HIGH));
        binding.chipRiskWatch.setOnClickListener(chip -> selectFilter(RiskLevel.WATCH));
        binding.chipRiskNormal.setOnClickListener(chip -> selectFilter(RiskLevel.NORMAL));

        if (bluetoothAdapter == null) {
            binding.statusHint.setText(R.string.wireless_bt_unsupported);
            binding.emptyText.setText(R.string.wireless_bt_unsupported);
            binding.emptyText.setVisibility(View.VISIBLE);
            binding.scanButton.setEnabled(false);
            return;
        }
        if (hasBluetoothPermission()) {
            startScan();
        } else {
            showPermissionPanel();
            requestPermission();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothDevice.ACTION_FOUND);
            filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            ContextCompat.registerReceiver(requireContext(), discoveryReceiver, filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
    }

    @Override
    public void onStop() {
        if (receiverRegistered) {
            requireContext().unregisterReceiver(discoveryReceiver);
            receiverRegistered = false;
        }
        stopScan();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        stopScan();
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
        binding = null;
    }

    private List<String> requiredPermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        return permissions;
    }

    private boolean hasBluetoothPermission() {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermission() {
        permissionLauncher.launch(requiredPermissions().toArray(new String[0]));
    }

    private boolean shouldShowRationale() {
        for (String permission : requiredPermissions()) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        return false;
    }

    private void showPermissionPanel() {
        binding.permissionPanel.setVisibility(View.VISIBLE);
        binding.permissionTitle.setText(R.string.wireless_bt_permission_title);
        binding.permissionDesc.setText(permissionPermanentlyDenied
                ? R.string.wireless_permission_denied : R.string.wireless_bt_permission_desc);
        binding.statusHint.setText(R.string.wireless_bt_empty);
    }

    private boolean isBluetoothEnabled() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } catch (SecurityException e) {
            return false;
        }
    }

    private void startScan() {
        if (binding == null || bluetoothAdapter == null) {
            return;
        }
        if (!isBluetoothEnabled()) {
            binding.statusHint.setText(R.string.wireless_bt_disabled);
            binding.emptyText.setText(R.string.wireless_bt_disabled);
            binding.emptyText.setVisibility(View.VISIBLE);
            return;
        }
        binding.permissionPanel.setVisibility(View.GONE);
        devices.clear();
        adapter.submit(new ArrayList<>());
        binding.resultSummary.setText("");
        binding.emptyText.setVisibility(View.GONE);
        scanning = true;
        binding.progressRow.setVisibility(View.VISIBLE);
        binding.scanButton.setEnabled(false);
        binding.scanButton.setText(R.string.wireless_scanning);
        binding.statusHint.setText(R.string.wireless_bt_scan_hint);

        try {
            bluetoothAdapter.cancelDiscovery();
            bluetoothAdapter.startDiscovery();
        } catch (SecurityException ignored) {
            // 权限不足时经典发现失败，BLE 扫描仍会尝试
        }
        try {
            bleScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bleScanner != null) {
                ScanSettings settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                bleScanner.startScan(null, settings, bleCallback);
            }
        } catch (SecurityException ignored) {
            // 无 BLE 或权限不足时只保留经典发现
        }
        handler.postDelayed(this::finishScan, SCAN_DURATION_MS);
    }

    /** 结束扫描并刷新汇总 */
    private void finishScan() {
        if (binding == null) {
            return;
        }
        stopScan();
        scanning = false;
        binding.progressRow.setVisibility(View.GONE);
        binding.scanButton.setEnabled(true);
        binding.scanButton.setText(R.string.wireless_scan_again);
        WirelessPrefs.saveScanTime(requireContext(), WirelessPrefs.KEY_BT);
        render();
        binding.statusHint.setText(getString(R.string.wireless_bt_scan_done, devices.size()));
        if (devices.isEmpty()) {
            binding.emptyText.setText(R.string.wireless_bt_empty);
            binding.emptyText.setVisibility(View.VISIBLE);
        }
    }

    private void stopScan() {
        try {
            if (bluetoothAdapter != null) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {
            // 权限被回收时忽略
        }
        try {
            if (bleScanner != null) {
                bleScanner.stopScan(bleCallback);
            }
        } catch (SecurityException ignored) {
            // 权限被回收时忽略
        }
        bleScanner = null;
    }

    private void onClassicFound(BluetoothDevice device, int rssi) {
        String name;
        try {
            name = device.getName();
        } catch (SecurityException e) {
            name = "";
        }
        List<String> uuids = new ArrayList<>();
        try {
            ParcelUuid[] parcelUuids = device.getUuids();
            if (parcelUuids != null) {
                for (ParcelUuid uuid : parcelUuids) {
                    uuids.add(uuid.toString());
                }
            }
        } catch (SecurityException ignored) {
            // 未授权时读不到缓存 UUID
        }
        mergeDevice(device.getAddress(), name, rssi, true, null, uuids);
    }

    private void onBleFound(ScanResult result) {
        BluetoothDevice device = result.getDevice();
        ScanRecord record = result.getScanRecord();
        String name = record == null ? null : record.getDeviceName();
        List<String> uuids = new ArrayList<>();
        if (record != null && record.getServiceUuids() != null) {
            for (ParcelUuid uuid : record.getServiceUuids()) {
                uuids.add(uuid.toString());
            }
        }
        mergeDevice(device.getAddress(), name, result.getRssi(), false,
                result.isConnectable(), uuids);
    }

    /** 合并经典与 BLE 的同一地址设备，并累计出现次数 */
    private void mergeDevice(String address, String name, int rssi, boolean classic,
                             Boolean connectable, List<String> uuids) {
        BtDevice device = devices.get(address);
        if (device == null) {
            String typeLabel = getString(classic
                    ? R.string.wireless_bt_type_classic : R.string.wireless_bt_type_ble);
            String connectableLabel = connectable == null
                    ? getString(R.string.wireless_bt_connectable_unknown)
                    : getString(connectable
                    ? R.string.wireless_bt_connectable_yes
                    : R.string.wireless_bt_connectable_no);
            device = new BtDevice(address, name, rssi, typeLabel, connectableLabel,
                    OuiLookup.vendorOf(address));
            device.sightings = 1;
            device.uuids.addAll(uuids);
            devices.put(address, device);
        } else {
            device.sightings++;
            device.rssi = rssi;
            if (device.name.isEmpty() && name != null && !name.isEmpty()) {
                device.name = name;
            }
            String bothLabel = getString(R.string.wireless_bt_type_both);
            String seenType = getString(classic
                    ? R.string.wireless_bt_type_classic : R.string.wireless_bt_type_ble);
            if (!bothLabel.equals(device.typeLabel) && !device.typeLabel.equals(seenType)) {
                device.typeLabel = bothLabel;
            }
            if (connectable != null) {
                device.connectableLabel = getString(connectable
                        ? R.string.wireless_bt_connectable_yes
                        : R.string.wireless_bt_connectable_no);
            }
            for (String uuid : uuids) {
                if (!device.uuids.contains(uuid)) {
                    device.uuids.add(uuid);
                }
            }
        }
        BtAnalyzer.evaluate(device);
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (refreshPosted) {
            return;
        }
        refreshPosted = true;
        handler.postDelayed(refreshRunnable, 300);
    }

    /** 主线程刷新列表与统计 */
    private void render() {
        refreshPosted = false;
        if (binding == null || !isAdded()) {
            return;
        }
        List<BtDevice> all = new ArrayList<>(devices.values());
        all.sort((first, second) -> {
            if (first.risk != second.risk) {
                return Integer.compare(first.risk.ordinal(), second.risk.ordinal());
            }
            return Integer.compare(second.rssi, first.rssi);
        });
        List<BtDevice> shown = new ArrayList<>();
        int high = 0;
        int watch = 0;
        for (BtDevice device : all) {
            if (device.risk == RiskLevel.HIGH) {
                high++;
            } else if (device.risk == RiskLevel.WATCH) {
                watch++;
            }
            if (filter == null || device.risk == filter) {
                shown.add(device);
            }
        }
        adapter.submit(shown);
        binding.resultSummary.setText(getString(R.string.wireless_scan_summary,
                all.size(), high, watch));
        if (shown.isEmpty() && !all.isEmpty()) {
            binding.emptyText.setText(R.string.wireless_filter_empty);
            binding.emptyText.setVisibility(View.VISIBLE);
        } else if (!all.isEmpty()) {
            binding.emptyText.setVisibility(View.GONE);
        }
    }

    private void selectFilter(RiskLevel level) {
        filter = level;
        binding.chipRiskAll.setChecked(level == null);
        binding.chipRiskHigh.setChecked(level == RiskLevel.HIGH);
        binding.chipRiskWatch.setChecked(level == RiskLevel.WATCH);
        binding.chipRiskNormal.setChecked(level == RiskLevel.NORMAL);
        render();
    }

    /** 点条目查看完整判定原因与能力边界提示 */
    private void showDetail(BtDevice device) {
        StringBuilder message = new StringBuilder();
        message.append("地址：").append(device.address);
        if (device.hasRandomAddress()) {
            message.append("（随机/本地管理地址）");
        }
        message.append('\n');
        message.append("厂商：").append(device.vendor).append('\n');
        message.append("信号：").append(device.rssi).append(" dBm\n");
        message.append("类型：").append(device.typeLabel).append('\n');
        message.append("连接性：").append(device.connectableLabel).append('\n');
        message.append("出现次数：").append(device.sightings).append('\n');
        message.append("服务：").append(device.uuids.isEmpty()
                ? "未获取" : String.valueOf(device.uuids)).append('\n');
        message.append("风险：").append(riskLabel(device.risk)).append("\n\n");
        if (device.reasons.isEmpty()) {
            message.append(getString(R.string.wireless_detail_no_reason));
        } else {
            for (int i = 0; i < device.reasons.size(); i++) {
                if (i > 0) {
                    message.append('\n');
                }
                message.append("· ").append(device.reasons.get(i));
            }
        }
        message.append("\n\n").append(getString(R.string.wireless_boundary_mac));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(device.displayName())
                .setMessage(message.toString())
                .setPositiveButton(R.string.wireless_detail_ok, null)
                .show();
    }

    private String riskLabel(RiskLevel level) {
        if (level == RiskLevel.HIGH) {
            return getString(R.string.wireless_risk_high);
        }
        if (level == RiskLevel.WATCH) {
            return getString(R.string.wireless_risk_watch);
        }
        return getString(R.string.wireless_risk_normal);
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + requireContext().getPackageName()));
        startActivity(intent);
    }
}
