package com.example.baseapp.ui.wireless;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.wireless.RiskLevel;
import com.example.baseapp.data.wireless.WifiAnalyzer;
import com.example.baseapp.data.wireless.WifiNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * WiFi 扫描结果列表：展示热点信息、风险标签与首条判定原因。
 */
public class WifiScanAdapter extends RecyclerView.Adapter<WifiScanAdapter.Holder> {

    public interface Listener {
        void onClick(WifiNetwork network);
    }

    private final List<WifiNetwork> items = new ArrayList<>();
    private final Listener listener;

    public WifiScanAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<WifiNetwork> networks) {
        items.clear();
        if (networks != null) {
            items.addAll(networks);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_wifi_network, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        WifiNetwork network = items.get(position);
        holder.ssidText.setText(network.displaySsid());
        holder.bssidText.setText(holder.itemView.getContext().getString(
                R.string.wireless_wifi_bssid, network.bssid, network.vendor));
        String meta = holder.itemView.getContext().getString(R.string.wireless_wifi_meta,
                network.rssi, network.band, network.security);
        holder.metaText.setText(meta + " · " + WifiAnalyzer.signalLabel(network.rssi));

        bindRisk(holder.riskBadge, network.risk, holder.itemView.getContext());
        if (network.reasons.isEmpty()) {
            holder.reasonText.setVisibility(View.GONE);
        } else {
            holder.reasonText.setVisibility(View.VISIBLE);
            holder.reasonText.setText(holder.itemView.getContext()
                    .getString(R.string.wireless_reason_prefix, network.reasons.get(0)));
        }
        holder.itemView.setOnClickListener(view -> listener.onClick(network));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 按风险等级切换标签文案与底色 */
    static void bindRisk(TextView badge, RiskLevel risk, android.content.Context context) {
        int textRes;
        int backgroundRes;
        if (risk == RiskLevel.HIGH) {
            textRes = R.string.wireless_risk_high;
            backgroundRes = R.drawable.bg_badge_high;
        } else if (risk == RiskLevel.WATCH) {
            textRes = R.string.wireless_risk_watch;
            backgroundRes = R.drawable.bg_badge_watch;
        } else {
            textRes = R.string.wireless_risk_normal;
            backgroundRes = R.drawable.bg_badge_normal;
        }
        badge.setText(textRes);
        badge.setBackgroundResource(backgroundRes);
        badge.setTextColor(context.getColor(R.color.menu_text_black));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView ssidText;
        final TextView bssidText;
        final TextView metaText;
        final TextView riskBadge;
        final TextView reasonText;

        Holder(@NonNull View itemView) {
            super(itemView);
            ssidText = itemView.findViewById(R.id.ssidText);
            bssidText = itemView.findViewById(R.id.bssidText);
            metaText = itemView.findViewById(R.id.metaText);
            riskBadge = itemView.findViewById(R.id.riskBadge);
            reasonText = itemView.findViewById(R.id.reasonText);
        }
    }
}
