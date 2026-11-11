package com.example.baseapp.ui.wireless;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.wireless.RiskLevel;
import com.example.baseapp.data.wireless.WifiAnalyzer;
import com.example.baseapp.data.wireless.WifiNetwork;
import com.example.baseapp.data.wireless.WirelessPrefs;
import com.example.baseapp.databinding.FragmentWifiScanBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * WiFi 扫描页：申请权限、启动系统扫描、按风险筛选并展示判定原因。
 */
public class WifiScanFragment extends Fragment {

    private FragmentWifiScanBinding binding;
    private WifiManager wifiManager;
    private WifiScanAdapter adapter;

    private final List<WifiNetwork> allNetworks = new ArrayList<>();
    private RiskLevel filter;
    private boolean scanning;
    private boolean receiverRegistered;
    private boolean permissionPermanentlyDenied;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 系统扫描结果就绪广播 */
    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction()) && scanning) {
                finishScan();
            }
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (binding == null) {
                    return;
                }
                if (hasWifiPermission()) {
                    permissionPermanentlyDenied = false;
                    binding.permissionPanel.setVisibility(View.GONE);
                    startScan();
                } else {
                    // 勾选「不再询问」后只能引导到系统设置
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
        binding = FragmentWifiScanBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wifiManager = (WifiManager) requireContext().getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        adapter = new WifiScanAdapter(this::showDetail);
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
            if (hasWifiPermission()) {
                startScan();
            } else {
                requestPermission();
            }
        });
        binding.chipRiskAll.setOnClickListener(chip -> selectFilter(null));
        binding.chipRiskHigh.setOnClickListener(chip -> selectFilter(RiskLevel.HIGH));
        binding.chipRiskWatch.setOnClickListener(chip -> selectFilter(RiskLevel.WATCH));
        binding.chipRiskNormal.setOnClickListener(chip -> selectFilter(RiskLevel.NORMAL));

        if (wifiManager == null) {
            binding.statusHint.setText(R.string.wireless_wifi_unsupported);
            binding.scanButton.setEnabled(false);
            return;
        }
        if (hasWifiPermission()) {
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
            ContextCompat.registerReceiver(requireContext(), scanReceiver,
                    new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
    }

    @Override
    public void onStop() {
        if (receiverRegistered) {
            requireContext().unregisterReceiver(scanReceiver);
            receiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        scanning = false;
        super.onDestroyView();
        binding = null;
    }

    private void requestPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        permissionLauncher.launch(new String[]{permission});
    }

    private boolean hasWifiPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return ContextCompat.checkSelfPermission(requireContext(), permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean shouldShowRationale() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.NEARBY_WIFI_DEVICES
                : Manifest.permission.ACCESS_FINE_LOCATION;
        return shouldShowRequestPermissionRationale(permission);
    }

    private void showPermissionPanel() {
        binding.permissionPanel.setVisibility(View.VISIBLE);
        binding.permissionTitle.setText(R.string.wireless_wifi_permission_title);
        binding.permissionDesc.setText(permissionPermanentlyDenied
                ? R.string.wireless_permission_denied : R.string.wireless_wifi_permission_desc);
        binding.statusHint.setText(R.string.wireless_wifi_empty_permission);
    }

    private void startScan() {
        if (binding == null || wifiManager == null) {
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            binding.statusHint.setText(R.string.wireless_wifi_disabled);
            binding.emptyText.setText(R.string.wireless_wifi_disabled);
            binding.emptyText.setVisibility(View.VISIBLE);
            return;
        }
        binding.permissionPanel.setVisibility(View.GONE);
        scanning = true;
        binding.progressRow.setVisibility(View.VISIBLE);
        binding.scanButton.setEnabled(false);
        binding.scanButton.setText(R.string.wireless_scanning);
        binding.emptyText.setVisibility(View.GONE);
        binding.statusHint.setText(R.string.wireless_scanning);

        boolean started;
        try {
            // 系统可能对 startScan 做限流，返回 false 时继续读取缓存结果
            started = wifiManager.startScan();
        } catch (SecurityException e) {
            finishScan();
            binding.statusHint.setText(getString(R.string.wireless_wifi_scan_failed,
                    e.getMessage() == null ? "权限不足" : e.getMessage()));
            return;
        }
        if (!started) {
            binding.statusHint.setText(R.string.wireless_wifi_throttled);
        }
        // 部分机型不发送结果广播，超时后主动读取一次
        handler.postDelayed(this::finishScan, 3000);
    }

    /** 结束扫描状态并按当前筛选刷新列表 */
    private void finishScan() {
        // 广播与超时都可能触发，这里只处理一次
        if (binding == null || !scanning) {
            return;
        }
        scanning = false;
        handler.removeCallbacksAndMessages(null);
        binding.progressRow.setVisibility(View.GONE);
        binding.scanButton.setEnabled(true);
        binding.scanButton.setText(R.string.wireless_scan_again);
        loadResults();
    }

    private void loadResults() {
        if (binding == null || wifiManager == null) {
            return;
        }
        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException e) {
            showPermissionPanel();
            return;
        }
        allNetworks.clear();
        allNetworks.addAll(WifiAnalyzer.analyze(results));
        WirelessPrefs.saveScanTime(requireContext(), WirelessPrefs.KEY_WIFI);
        applyFilter();
        if (allNetworks.isEmpty()) {
            // 空结果的常见原因逐个说明，给可操作提示
            if (Build.VERSION.SDK_INT < 33 && !isLocationEnabled()) {
                binding.emptyText.setText(R.string.wireless_wifi_empty_location);
            } else {
                binding.emptyText.setText(R.string.wireless_wifi_empty);
            }
            binding.emptyText.setVisibility(View.VISIBLE);
        }
        binding.statusHint.setText(getString(R.string.wireless_wifi_scan_done, allNetworks.size()));
    }

    private boolean isLocationEnabled() {
        LocationManager manager = (LocationManager) requireContext()
                .getSystemService(Context.LOCATION_SERVICE);
        return manager != null && manager.isLocationEnabled();
    }

    private void selectFilter(RiskLevel level) {
        filter = level;
        binding.chipRiskAll.setChecked(level == null);
        binding.chipRiskHigh.setChecked(level == RiskLevel.HIGH);
        binding.chipRiskWatch.setChecked(level == RiskLevel.WATCH);
        binding.chipRiskNormal.setChecked(level == RiskLevel.NORMAL);
        applyFilter();
    }

    private void applyFilter() {
        if (binding == null) {
            return;
        }
        List<WifiNetwork> shown = new ArrayList<>();
        int high = 0;
        int watch = 0;
        for (WifiNetwork network : allNetworks) {
            if (network.risk == RiskLevel.HIGH) {
                high++;
            } else if (network.risk == RiskLevel.WATCH) {
                watch++;
            }
            if (filter == null || network.risk == filter) {
                shown.add(network);
            }
        }
        adapter.submit(shown);
        binding.resultSummary.setText(getString(R.string.wireless_scan_summary,
                allNetworks.size(), high, watch));
        if (shown.isEmpty() && !allNetworks.isEmpty()) {
            binding.emptyText.setText(R.string.wireless_filter_empty);
            binding.emptyText.setVisibility(View.VISIBLE);
        } else if (!allNetworks.isEmpty()) {
            binding.emptyText.setVisibility(View.GONE);
        }
    }

    /** 点条目查看完整判定原因 */
    private void showDetail(WifiNetwork network) {
        StringBuilder message = new StringBuilder();
        message.append("BSSID：").append(network.bssid).append('\n');
        message.append("厂商：").append(network.vendor).append('\n');
        message.append("频段：").append(network.band).append('\n');
        message.append("加密：").append(network.security).append('\n');
        message.append("风险：").append(riskLabel(network.risk)).append("\n\n");
        if (network.reasons.isEmpty()) {
            message.append(getString(R.string.wireless_detail_no_reason));
        } else {
            for (int i = 0; i < network.reasons.size(); i++) {
                if (i > 0) {
                    message.append('\n');
                }
                message.append("· ").append(network.reasons.get(i));
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(network.displaySsid())
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
