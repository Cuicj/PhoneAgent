package com.example.baseapp.ui.sensor;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.sensor.SensorInfo;
import com.example.baseapp.databinding.ItemSensorInfoBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 传感器自检清单适配器。
 */
public class SensorCheckAdapter extends RecyclerView.Adapter<SensorCheckAdapter.Holder> {

    private final List<SensorInfo> items = new ArrayList<>();

    public void submit(List<SensorInfo> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSensorInfoBinding binding = ItemSensorInfoBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SensorInfo info = items.get(position);
        holder.binding.sensorName.setText(info.name);
        holder.binding.sensorType.setText(holder.itemView.getContext()
                .getString(R.string.sensor_check_item_type, info.typeName));
        holder.binding.sensorVendor.setText(holder.itemView.getContext()
                .getString(R.string.sensor_check_item_vendor, info.vendor));
        holder.binding.sensorSpec.setText(holder.itemView.getContext()
                .getString(R.string.sensor_check_item_spec, info.maxRange, info.resolution));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemSensorInfoBinding binding;

        Holder(ItemSensorInfoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
