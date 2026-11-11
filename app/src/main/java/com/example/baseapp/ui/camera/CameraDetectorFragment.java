package com.example.baseapp.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.databinding.FragmentCameraDetectorBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 暗光检测页：用后置摄像头观察暗处的红外亮点，帮助寻找隐藏摄像头。
 * 依赖摄像头对红外的敏感性，与红外发射器无关。
 */
public class CameraDetectorFragment extends Fragment {

    private FragmentCameraDetectorBinding binding;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService analysisExecutor;
    private CameraFrameProcessor frameProcessor;

    private float gain = 1.8f;
    private float contrast = 1.1f;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (binding == null) {
                    return;
                }
                if (granted) {
                    startCameraIfPermitted();
                } else {
                    binding.permissionPanel.setVisibility(View.VISIBLE);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCameraDetectorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupAdjustments();
        binding.grantButton.setOnClickListener(button -> cameraPermissionLauncher.launch(
                Manifest.permission.CAMERA));
        if (hasCameraPermission()) {
            startCameraIfPermitted();
        } else {
            // 首次进入主动申请，拒绝后保留权限面板可重试
            binding.permissionPanel.setVisibility(View.VISIBLE);
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_camera_detector);
    }

    @Override
    public void onDestroyView() {
        // 页面销毁时释放相机与线程，避免泄漏
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        if (analysisExecutor != null) {
            analysisExecutor.shutdown();
            analysisExecutor = null;
        }
        frameProcessor = null;
        super.onDestroyView();
        binding = null;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void setupAdjustments() {
        binding.gainSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                gain = 1f + progress / 100f;
                binding.gainLabel.setText(getString(R.string.camera_detector_gain, gain));
                if (frameProcessor != null) {
                    frameProcessor.setGain(gain);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        binding.contrastSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                contrast = 0.5f + progress / 100f;
                binding.contrastLabel.setText(getString(R.string.camera_detector_contrast, contrast));
                if (frameProcessor != null) {
                    frameProcessor.setContrast(contrast);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        // 初始化进度会同时刷新标签与参数
        binding.gainSeek.setProgress(80);
        binding.contrastSeek.setProgress(60);
        // 进度与 XML 默认值一致时不会触发回调，这里主动刷新一次标签
        binding.gainLabel.setText(getString(R.string.camera_detector_gain, gain));
        binding.contrastLabel.setText(getString(R.string.camera_detector_contrast, contrast));
    }

    private void startCameraIfPermitted() {
        if (binding == null) {
            return;
        }
        if (!hasCameraPermission()) {
            binding.permissionPanel.setVisibility(View.VISIBLE);
            return;
        }
        binding.permissionPanel.setVisibility(View.GONE);
        if (analysisExecutor == null) {
            analysisExecutor = Executors.newSingleThreadExecutor();
        }
        if (frameProcessor == null) {
            frameProcessor = new CameraFrameProcessor(binding.previewImage);
            frameProcessor.setGain(gain);
            frameProcessor.setContrast(contrast);
        }
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            if (binding == null) {
                return;
            }
            try {
                cameraProvider = future.get();
                ResolutionSelector selector = new ResolutionSelector.Builder()
                        .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build();
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();
                analysis.setAnalyzer(analysisExecutor, frameProcessor);
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(),
                        CameraSelector.DEFAULT_BACK_CAMERA, analysis);
            } catch (Exception e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                Toast.makeText(requireContext(),
                        getString(R.string.camera_detector_camera_failed, message),
                        Toast.LENGTH_LONG).show();
                binding.permissionPanel.setVisibility(View.VISIBLE);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }
}
