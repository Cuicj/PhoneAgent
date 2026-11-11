package com.example.baseapp.ui.device;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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

import com.example.baseapp.R;
import com.example.baseapp.data.device.BatterySnapshot;
import com.example.baseapp.data.device.DeviceInfoCollector;
import com.example.baseapp.data.device.InfoGroup;
import com.example.baseapp.data.device.InfoItem;
import com.example.baseapp.databinding.FragmentDeviceInfoBinding;
import com.example.baseapp.databinding.ItemDeviceGroupBinding;
import com.example.baseapp.databinding.ItemDeviceRowBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备信息总览：品牌型号、芯片、电池（含循环次数）、序列号与系统版本。
 * 电池数据通过 ACTION_BATTERY_CHANGED 广播随充电状态刷新，页面不可见时注销。
 */
public class DeviceInfoFragment extends Fragment {

    private FragmentDeviceInfoBinding binding;
    /** 当前电池快照，空表示尚未收到广播 */
    private BatterySnapshot lastBattery;
    /** 当前渲染的分组数据，供复制使用 */
    private final List<InfoGroup> groups = new ArrayList<>();
    /** 电池广播是否已注册，避免重复注销 */
    private boolean receiverRegistered;

    /** 电池状态变化时刷新电池分组与刷新时间 */
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            lastBattery = BatterySnapshot.from(intent);
            if (binding != null) {
                renderAll();
            }
        }
    };

    /** 申请电话状态权限，用于尝试读取序列号 */
    private final ActivityResultLauncher<String> phoneStateLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding == null) {
                    return;
                }
                if (!granted) {
                    Toast.makeText(requireContext(),
                            R.string.device_serial_permission_denied, Toast.LENGTH_LONG).show();
                }
                renderAll();
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDeviceInfoBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.copyAllButton.setOnClickListener(button -> copyAll());
        // 长按页面空白处或分组卡片也可复制，方便单手操作
        binding.deviceRoot.setOnLongClickListener(item -> {
            copyAll();
            return true;
        });
        binding.serialPermissionButton.setOnClickListener(button -> {
            if (!DeviceInfoCollector.hasPhoneStatePermission(requireContext())) {
                phoneStateLauncher.launch(android.Manifest.permission.READ_PHONE_STATE);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_device);
        // 注册电池广播：注册时系统会立即回传当前状态（粘性广播）
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky = ContextCompat.registerReceiver(requireContext(), batteryReceiver,
                filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
        if (sticky != null) {
            lastBattery = BatterySnapshot.from(sticky);
        }
        renderAll();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (receiverRegistered) {
            try {
                requireContext().unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {
                // 极端情况下系统已回收注册，忽略即可
            }
            receiverRegistered = false;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /** 重新采集并渲染全部信息卡片 */
    private void renderAll() {
        if (binding == null) {
            return;
        }
        Context context = requireContext();
        groups.clear();
        groups.addAll(DeviceInfoCollector.collectAll(context, lastBattery));

        binding.groupContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(context);
        for (InfoGroup group : groups) {
            ItemDeviceGroupBinding groupBinding = ItemDeviceGroupBinding
                    .inflate(inflater, binding.groupContainer, false);
            groupBinding.groupTitle.setText(group.title);
            for (InfoItem item : group.items) {
                ItemDeviceRowBinding rowBinding = ItemDeviceRowBinding
                        .inflate(inflater, groupBinding.rowsContainer, false);
                rowBinding.rowLabel.setText(item.label);
                rowBinding.rowValue.setText(item.value);
                groupBinding.rowsContainer.addView(rowBinding.getRoot());
            }
            // 长按分组卡片复制全部信息
            groupBinding.getRoot().setOnLongClickListener(view -> {
                copyAll();
                return true;
            });
            binding.groupContainer.addView(groupBinding.getRoot());
        }

        binding.updateStatus.setText(getString(R.string.device_update_status,
                DeviceInfoCollector.nowText()));
        // 未授权时提供申请入口；已授权则隐藏
        binding.serialPermissionButton.setVisibility(
                DeviceInfoCollector.hasPhoneStatePermission(context) ? View.GONE : View.VISIBLE);
    }

    /** 复制全部设备信息到剪贴板 */
    private void copyAll() {
        if (binding == null || groups.isEmpty()) {
            return;
        }
        Context context = requireContext();
        ClipboardManager clipboard = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(context, R.string.device_copy_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        String text = DeviceInfoCollector.toPlainText(context, groups);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.title_device), text));
        Toast.makeText(context, R.string.device_copy_done, Toast.LENGTH_SHORT).show();
    }
}
