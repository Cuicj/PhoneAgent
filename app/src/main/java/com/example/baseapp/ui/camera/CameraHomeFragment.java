package com.example.baseapp.ui.camera;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.baseapp.R;
import com.example.baseapp.databinding.FragmentCameraHomeBinding;

/**
 * 相机功能页：说明暗光检测原理与用途，入口进入相机预览。
 */
public class CameraHomeFragment extends Fragment {

    private FragmentCameraHomeBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCameraHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 纯文字入口：进入暗光检测（相机预览）
        binding.detectorEntry.setOnClickListener(entry -> NavHostFragment.findNavController(this)
                .navigate(R.id.action_cameraHome_to_cameraDetector));
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_camera);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
