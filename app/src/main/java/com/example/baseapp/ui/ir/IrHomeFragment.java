package com.example.baseapp.ui.ir;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.baseapp.R;
import com.example.baseapp.data.IrCapability;
import com.example.baseapp.databinding.FragmentIrHomeBinding;

/**
 * 红外功能说明页：展示硬件检测结论、遥控入口与生活用途清单。
 */
public class IrHomeFragment extends Fragment {

    private FragmentIrHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIrHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        boolean supported = IrCapability.hasEmitter(requireContext());
        if (supported) {
            binding.statusBadge.setText(R.string.ir_supported);
            binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_granted);
            binding.statusBadge.setTextColor(requireContext().getColor(
                    R.color.brand_on_primary_container));
            binding.statusDesc.setText(R.string.ir_supported_desc);
        } else {
            binding.statusBadge.setText(R.string.ir_unsupported);
            binding.statusBadge.setBackgroundResource(R.drawable.bg_badge_normal);
            binding.statusBadge.setTextColor(requireContext().getColor(
                    R.color.on_access_container));
            binding.statusDesc.setText(R.string.ir_unsupported_reason);
        }

        // 遥控入口始终可点击；无红外时遥控页只展示编码说明
        binding.remoteEntry.setOnClickListener(entry -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_irHome_to_irRemote));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_ir);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
