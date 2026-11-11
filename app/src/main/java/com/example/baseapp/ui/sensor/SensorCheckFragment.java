package com.example.baseapp.ui.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.sensor.SensorCatalog;
import com.example.baseapp.data.sensor.SensorInfo;
import com.example.baseapp.databinding.FragmentSensorCheckBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 传感器自检清单：列出本机全部可用传感器，并单独提示关键传感器缺失。
 */
public class SensorCheckFragment extends Fragment {

    private FragmentSensorCheckBinding binding;
    private SensorCheckAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSensorCheckBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        adapter = new SensorCheckAdapter();
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        loadSensors();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_sensor_check);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadSensors() {
        Context context = requireContext();
        SensorManager sensorManager = (SensorManager) context
                .getSystemService(Context.SENSOR_SERVICE);
        List<SensorInfo> items = new ArrayList<>();
        if (sensorManager != null) {
            for (Sensor sensor : sensorManager.getSensorList(Sensor.TYPE_ALL)) {
                items.add(new SensorInfo(
                        sensor.getName(),
                        SensorCatalog.typeName(context, sensor),
                        sensor.getVendor(),
                        SensorCatalog.formatValue(sensor.getMaximumRange()),
                        SensorCatalog.formatValue(sensor.getResolution())));
            }
        }
        adapter.submit(items);
        binding.summary.setText(getString(R.string.sensor_check_summary, items.size()));

        String missing = SensorCatalog.missingKeyNames(context);
        if (missing.isEmpty()) {
            binding.missingCard.setVisibility(View.GONE);
        } else {
            binding.missingCard.setVisibility(View.VISIBLE);
            binding.missingText.setText(getString(R.string.sensor_check_missing, missing));
        }

        boolean empty = items.isEmpty();
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }
}
