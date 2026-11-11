package com.example.baseapp.ui.wireless;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.wireless.BtDevice;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝牙扫描结果列表：展示设备名、地址、RSSI、类型、连接性与服务 UUID。
 */
public class BtScanAdapter extends RecyclerView.Adapter<BtScanAdapter.Holder> {

    public interface Listener {
        void onClick(BtDevice device);
    }

    private final List<BtDevice> items = new ArrayList<>();
    private final Listener listener;

    public BtScanAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<BtDevice> devices) {
        items.clear();
        if (devices != null) {
            items.addAll(devices);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bluetooth_device, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        BtDevice device = items.get(position);
        holder.nameText.setText(device.displayName());
        String vendor = device.vendor;
        if (device.hasRandomAddress()) {
            vendor = vendor + " · " + holder.itemView.getContext()
                    .getString(R.string.wireless_bt_random_mac);
        }
        holder.addressText.setText(holder.itemView.getContext()
                .getString(R.string.wireless_bt_address, device.address, vendor));
        holder.metaText.setText(holder.itemView.getContext().getString(R.string.wireless_bt_meta,
                device.rssi, device.typeLabel, device.connectableLabel));
        if (device.uuids.isEmpty()) {
            holder.uuidText.setText(R.string.wireless_bt_uuid_none);
        } else {
            holder.uuidText.setText(holder.itemView.getContext()
                    .getString(R.string.wireless_bt_uuid, joinUuids(device.uuids)));
        }
        holder.uuidText.setVisibility(View.VISIBLE);

        WifiScanAdapter.bindRisk(holder.riskBadge, device.risk, holder.itemView.getContext());
        if (device.reasons.isEmpty()) {
            holder.reasonText.setVisibility(View.GONE);
        } else {
            holder.reasonText.setVisibility(View.VISIBLE);
            holder.reasonText.setText(holder.itemView.getContext()
                    .getString(R.string.wireless_reason_prefix, device.reasons.get(0)));
        }
        holder.itemView.setOnClickListener(view -> listener.onClick(device));
    }

    /** 服务 UUID 较多时只展示前两个，避免列表过高（String.join 需要 API 26，这里手动拼接） */
    private String joinUuids(List<String> uuids) {
        if (uuids.size() <= 2) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < uuids.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(uuids.get(i));
            }
            return builder.toString();
        }
        return uuids.get(0) + ", " + uuids.get(1) + "…";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView nameText;
        final TextView addressText;
        final TextView metaText;
        final TextView uuidText;
        final TextView riskBadge;
        final TextView reasonText;

        Holder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            addressText = itemView.findViewById(R.id.addressText);
            metaText = itemView.findViewById(R.id.metaText);
            uuidText = itemView.findViewById(R.id.uuidText);
            riskBadge = itemView.findViewById(R.id.riskBadge);
            reasonText = itemView.findViewById(R.id.reasonText);
        }
    }
}
