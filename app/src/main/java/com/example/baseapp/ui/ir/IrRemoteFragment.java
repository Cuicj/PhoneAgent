package com.example.baseapp.ui.ir;

import android.hardware.ConsumerIrManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.IrCapability;
import com.example.baseapp.data.IrPresets;
import com.example.baseapp.data.NecEncoder;
import com.example.baseapp.databinding.FragmentIrRemoteBinding;
import com.example.baseapp.databinding.ItemIrPresetBinding;

import java.util.Locale;

/**
 * 红外遥控页：内置示例 NEC 电源码并支持手动发送。
 * 无红外硬件时发送会给出明确提示，页面仍可查看编码说明。
 */
public class IrRemoteFragment extends Fragment {

    private FragmentIrRemoteBinding binding;
    private boolean supported;
    private IrPresets.DeviceType deviceFilter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIrRemoteBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        supported = IrCapability.hasEmitter(requireContext());
        binding.hardwareStatus.setText(supported
                ? R.string.ir_supported_desc : R.string.ir_remote_no_hardware);
        // 无硬件时禁用发送按钮，但保留预设与说明供查看
        binding.sendButton.setEnabled(supported);
        binding.sendButton.setOnClickListener(button -> sendFromInput());
        setupDeviceFilters();
        renderPresets();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_ir_remote);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupDeviceFilters() {
        binding.deviceFilterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.chipDeviceTv) {
                deviceFilter = IrPresets.DeviceType.TV;
            } else if (checkedId == R.id.chipDeviceAc) {
                deviceFilter = IrPresets.DeviceType.AC;
            } else if (checkedId == R.id.chipDeviceFan) {
                deviceFilter = IrPresets.DeviceType.FAN;
            } else if (checkedId == R.id.chipDeviceProjector) {
                deviceFilter = IrPresets.DeviceType.PROJECTOR;
            } else {
                deviceFilter = null;
            }
            renderPresets();
        });
    }

    private void renderPresets() {
        binding.presetList.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (IrPresets.Preset preset : IrPresets.filter(deviceFilter)) {
            ItemIrPresetBinding item = ItemIrPresetBinding.inflate(
                    inflater, binding.presetList, false);
            item.presetBrand.setText(preset.getBrand());
            item.presetCode.setText(getString(R.string.ir_preset_item_desc,
                    preset.getAddress(), preset.getCommand()));
            item.getRoot().setOnClickListener(view ->
                    sendNec(preset.getAddress(), preset.getCommand()));
            binding.presetList.addView(item.getRoot());
        }
    }

    private void sendFromInput() {
        Integer address = parseCode(binding.addressInput.getText());
        Integer command = parseCode(binding.commandInput.getText());
        if (address == null || command == null
                || address < 0 || address > 0xFFFF
                || command < 0 || command > 0xFF) {
            Toast.makeText(requireContext(), R.string.ir_remote_invalid_code,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        sendNec(address, command);
    }

    /** 支持十进制与 0x 前缀十六进制 */
    private Integer parseCode(CharSequence raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return Integer.parseInt(text.substring(2), 16);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 发送前校验红外硬件与 38000 Hz 载波支持，任何不满足都明确提示。
     */
    private void sendNec(int address, int command) {
        ConsumerIrManager manager = (ConsumerIrManager) requireContext()
                .getSystemService(android.content.Context.CONSUMER_IR_SERVICE);
        if (!supported || manager == null) {
            Toast.makeText(requireContext(), R.string.ir_remote_send_unsupported,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ConsumerIrManager.CarrierFrequencyRange[] frequencies;
        try {
            // 公开 API 为 getCarrierFrequencies()，返回支持范围
            frequencies = manager.getCarrierFrequencies();
        } catch (RuntimeException e) {
            Toast.makeText(requireContext(), R.string.ir_remote_freq_unknown,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (frequencies == null || frequencies.length == 0) {
            Toast.makeText(requireContext(), R.string.ir_remote_freq_unknown,
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!supportsFrequency(frequencies, NecEncoder.DEFAULT_FREQUENCY)) {
            Toast.makeText(requireContext(), getString(R.string.ir_remote_freq_unsupported,
                    formatFrequencies(frequencies)), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            manager.transmit(NecEncoder.DEFAULT_FREQUENCY, NecEncoder.encode(address, command));
            Toast.makeText(requireContext(), R.string.ir_remote_send_success,
                    Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Toast.makeText(requireContext(),
                    getString(R.string.ir_remote_send_failed, message), Toast.LENGTH_LONG).show();
        }
    }

    /** 载波范围为两两成对的区间，命中任一区间即可发送 */
    private boolean supportsFrequency(ConsumerIrManager.CarrierFrequencyRange[] ranges,
                                      int target) {
        for (ConsumerIrManager.CarrierFrequencyRange range : ranges) {
            if (range != null && target >= range.getMinFrequency()
                    && target <= range.getMaxFrequency()) {
                return true;
            }
        }
        return false;
    }

    private String formatFrequencies(ConsumerIrManager.CarrierFrequencyRange[] ranges) {
        StringBuilder builder = new StringBuilder();
        for (ConsumerIrManager.CarrierFrequencyRange range : ranges) {
            if (range == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(String.format(Locale.CHINA, "%.0f–%.0f kHz",
                    range.getMinFrequency() / 1000f, range.getMaxFrequency() / 1000f));
        }
        return builder.toString();
    }
}
