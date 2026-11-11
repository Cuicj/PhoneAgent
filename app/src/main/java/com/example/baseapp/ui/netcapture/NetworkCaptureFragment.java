package com.example.baseapp.ui.netcapture;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.netcapture.CaptureRecord;
import com.example.baseapp.data.netcapture.CaptureState;
import com.example.baseapp.data.netcapture.CaptureStore;
import com.example.baseapp.databinding.FragmentNetworkCaptureBinding;
import com.example.baseapp.net.CaptureVpnService;
import com.example.baseapp.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 网络请求抓取列表：开关、筛选、实时列表与右上角清除。
 */
public class NetworkCaptureFragment extends Fragment {

    /** 列表刷新间隔，避免高频 notify 造成卡顿 */
    private static final long REFRESH_INTERVAL_MS = 600L;
    /** 服务启动超时，超时后提示失败而不是一直显示启动中 */
    private static final long START_TIMEOUT_MS = 5000L;

    /** 状态筛选：0 全部、1 2xx、2 3xx、3 4xx、4 5xx、5 无状态码 */
    private static final int FILTER_ALL = 0;
    private static final int FILTER_NONE = 5;

    private FragmentNetworkCaptureBinding binding;
    private CaptureAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean updatingSwitch;
    private boolean permissionPending;
    private long startRequestedAt;
    /** 界面侧错误文案：避免被下一次轮询覆盖成「未开启」 */
    private String uiError;

    private final ActivityResultLauncher<Intent> vpnLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                permissionPending = false;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    startCaptureService();
                } else if (binding != null) {
                    startRequestedAt = 0;
                    setSwitchChecked(false);
                    binding.statusText.setText(R.string.capture_vpn_denied);
                }
            });

    private final ActivityResultLauncher<String> notificationLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> requestVpnPermission());

    /** 右上角清除入口 */
    private final MenuProvider menuProvider = new MenuProvider() {
        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            inflater.inflate(R.menu.menu_capture_list, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() == R.id.action_capture_clear) {
                confirmClear();
                return true;
            }
            return false;
        }
    };

    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (binding == null) {
                return;
            }
            refresh();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentNetworkCaptureBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new CaptureAdapter(this::openDetail);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);

        // 状态筛选下拉：全部 / 2xx / 3xx / 4xx / 5xx / 无状态码
        ArrayAdapter<CharSequence> statusAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.capture_status_options, android.R.layout.simple_spinner_item);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.statusFilter.setAdapter(statusAdapter);
        binding.statusFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position, long id) {
                refresh();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        binding.domainFilter.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                refresh();
            }
        });
        binding.failedCheck.setOnCheckedChangeListener((button, checked) -> refresh());

        binding.captureSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSwitch) {
                return;
            }
            if (checked) {
                startRequestedAt = System.currentTimeMillis();
                requestNotificationThenVpn();
            } else {
                stopCaptureService();
            }
        });

        requireActivity().addMenuProvider(menuProvider, getViewLifecycleOwner(),
                Lifecycle.State.RESUMED);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_capture);
        setSwitchChecked(CaptureState.running);
        handler.post(refreshTask);
    }

    @Override
    public void onPause() {
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        // 进入详情页或屏幕旋转时保持抓取；真正离开抓取模块才释放 tun，避免 VPN 图标常驻
        if (!isNavigatingToDetail() && !isChangingConfiguration()) {
            stopCaptureService();
        }
        super.onDestroyView();
        binding = null;
    }

    /** 配置变更（旋转等）会导致 Fragment 重建，此时不应停止抓取 */
    private boolean isChangingConfiguration() {
        return getActivity() != null && getActivity().isChangingConfigurations();
    }

    /** Android 13+ 先申请通知权限，保证前台服务通知可见 */
    private void requestNotificationThenVpn() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            requestVpnPermission();
        }
    }

    /** 申请 VPN 授权；已授权时直接启动服务 */
    private void requestVpnPermission() {
        if (binding == null) {
            return;
        }
        Intent prepareIntent;
        try {
            prepareIntent = VpnService.prepare(requireContext());
        } catch (Exception e) {
            failStart(getString(R.string.capture_status_error, Formats.errorMessage(e)));
            return;
        }
        if (prepareIntent != null) {
            permissionPending = true;
            binding.statusText.setText(R.string.capture_status_waiting_vpn);
            try {
                vpnLauncher.launch(prepareIntent);
            } catch (Exception e) {
                permissionPending = false;
                failStart(getString(R.string.capture_status_error, Formats.errorMessage(e)));
            }
        } else {
            startCaptureService();
        }
    }

    private void startCaptureService() {
        if (binding == null) {
            return;
        }
        startRequestedAt = System.currentTimeMillis();
        binding.statusText.setText(R.string.capture_status_starting);
        try {
            Intent intent = new Intent(requireContext(), CaptureVpnService.class)
                    .setAction(CaptureVpnService.ACTION_START);
            ContextCompat.startForegroundService(requireContext(), intent);
        } catch (Exception e) {
            failStart(getString(R.string.capture_status_error, Formats.errorMessage(e)));
        }
    }

    private void stopCaptureService() {
        startRequestedAt = 0;
        permissionPending = false;
        uiError = null;
        if (getContext() == null) {
            return;
        }
        // VpnService 被系统绑定，stopService 不会销毁实例，必须显式通知服务关闭 tun
        try {
            Intent intent = new Intent(requireContext(), CaptureVpnService.class)
                    .setAction(CaptureVpnService.ACTION_STOP);
            requireContext().startService(intent);
        } catch (Exception ignored) {
        }
    }

    private void failStart(String message) {
        permissionPending = false;
        startRequestedAt = 0;
        uiError = message;
        setSwitchChecked(false);
        if (binding != null) {
            binding.statusText.setText(message);
        }
    }

    /** 定时刷新：读取快照、按条件筛选并更新状态文案 */
    private void refresh() {
        if (binding == null) {
            return;
        }
        List<CaptureRecord> all = CaptureStore.get().snapshot();
        String keyword = binding.domainFilter.getText() == null
                ? "" : binding.domainFilter.getText().toString().trim().toLowerCase(Locale.ROOT);
        int statusMode = binding.statusFilter.getSelectedItemPosition();
        boolean onlyFailed = binding.failedCheck.isChecked();

        List<CaptureRecord> shown = new ArrayList<>();
        int activeCount = 0;
        for (CaptureRecord record : all) {
            if (!record.finished) {
                activeCount++;
            }
            if (matches(record, keyword, statusMode, onlyFailed)) {
                shown.add(record);
            }
        }
        adapter.submit(shown);
        binding.summaryText.setText(getString(R.string.capture_summary,
                all.size(), shown.size(), activeCount));
        binding.emptyText.setText(all.isEmpty() ? R.string.capture_empty : R.string.capture_filter_empty);
        binding.emptyText.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);

        updateStatus(all);
    }

    private boolean matches(CaptureRecord record, String keyword, int statusMode, boolean onlyFailed) {
        if (onlyFailed && !record.failed) {
            return false;
        }
        if (!keyword.isEmpty()) {
            String target = record.displayTarget() == null
                    ? "" : record.displayTarget().toLowerCase(Locale.ROOT);
            String ip = record.remoteIp == null ? "" : record.remoteIp.toLowerCase(Locale.ROOT);
            if (!target.contains(keyword) && !ip.contains(keyword)) {
                return false;
            }
        }
        int code = record.statusCode;
        if (statusMode == FILTER_ALL) {
            return true;
        }
        if (statusMode == FILTER_NONE) {
            return code <= 0;
        }
        if (code <= 0) {
            return false;
        }
        switch (statusMode) {
            case 1:
                return code >= 200 && code < 300;
            case 2:
                return code >= 300 && code < 400;
            case 3:
                return code >= 400 && code < 500;
            case 4:
                return code >= 500 && code < 600;
            default:
                return true;
        }
    }

    /** 同步开关与状态文案；启动中/等待授权时保留当前展示 */
    private void updateStatus(List<CaptureRecord> all) {
        if (permissionPending) {
            binding.statusText.setText(R.string.capture_status_waiting_vpn);
            return;
        }
        if (startRequestedAt > 0) {
            if (CaptureState.running) {
                startRequestedAt = 0;
            } else if (CaptureState.lastError != null
                    || System.currentTimeMillis() - startRequestedAt > START_TIMEOUT_MS) {
                String error = CaptureState.lastError != null
                        ? CaptureState.lastError : getString(R.string.capture_status_timeout);
                failStart(getString(R.string.capture_status_error, error));
                return;
            } else {
                binding.statusText.setText(R.string.capture_status_starting);
                return;
            }
        }
        if (CaptureState.running) {
            uiError = null;
            setSwitchChecked(true);
            binding.statusText.setText(getString(R.string.capture_status_running, all.size()));
        } else {
            setSwitchChecked(false);
            if (uiError != null) {
                binding.statusText.setText(uiError);
            } else {
                binding.statusText.setText(CaptureState.lastError != null
                        ? getString(R.string.capture_status_error, CaptureState.lastError)
                        : getString(R.string.capture_status_off));
            }
        }
    }

    /** 更新开关但不触发监听，避免与用户操作互相覆盖 */
    private void setSwitchChecked(boolean checked) {
        if (binding == null || binding.captureSwitch.isChecked() == checked) {
            return;
        }
        updatingSwitch = true;
        binding.captureSwitch.setChecked(checked);
        updatingSwitch = false;
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.capture_clear_title)
                .setMessage(R.string.capture_clear_message)
                .setNegativeButton(R.string.capture_cancel, null)
                .setPositiveButton(R.string.capture_clear_confirm, (dialog, which) -> {
                    CaptureStore.get().clear();
                    refresh();
                    Toast.makeText(requireContext(), R.string.capture_cleared, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void openDetail(CaptureRecord record) {
        Bundle args = new Bundle();
        args.putLong("recordId", record.id);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_networkCapture_to_captureDetail, args);
    }

    /** 判断当前是否正在进入详情页，用于决定离开列表时是否停止抓取 */
    private boolean isNavigatingToDetail() {
        try {
            NavController controller = NavHostFragment.findNavController(this);
            return controller.getCurrentDestination() != null
                    && controller.getCurrentDestination().getId() == R.id.captureDetail;
        } catch (Exception e) {
            return false;
        }
    }
}
