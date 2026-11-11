package com.example.baseapp.ui.netcapture;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.netcapture.CaptureRecord;
import com.example.baseapp.util.Formats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 抓取记录列表：时间、方法、目标、状态码/HTTPS 标记、耗时与大小。
 */
public class CaptureAdapter extends RecyclerView.Adapter<CaptureAdapter.Holder> {

    public interface Listener {
        void onClick(CaptureRecord record);
    }

    private final List<CaptureRecord> items = new ArrayList<>();
    private final Listener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);

    public CaptureAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<CaptureRecord> records) {
        items.clear();
        if (records != null) {
            items.addAll(records);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_capture, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        CaptureRecord record = items.get(position);
        Context context = holder.itemView.getContext();

        holder.methodText.setText(protocolLabel(record));
        holder.targetText.setText(record.displayTarget());
        bindBadge(holder.statusBadge, record);

        String meta = context.getString(R.string.capture_item_meta,
                timeFormat.format(new Date(record.startTime)),
                record.durationMs(),
                Formats.formatBytes(record.upBytes),
                Formats.formatBytes(record.downBytes));
        if (!record.finished) {
            meta = meta + " · " + context.getString(R.string.capture_in_progress);
        }
        holder.metaText.setText(meta);

        String note = record.note;
        if (note == null && record.isEncrypted()) {
            note = context.getString(R.string.capture_item_https_note);
        }
        if (note == null || note.isEmpty()) {
            holder.noteText.setVisibility(View.GONE);
        } else {
            holder.noteText.setVisibility(View.VISIBLE);
            holder.noteText.setText(note);
        }

        holder.itemView.setOnClickListener(view -> listener.onClick(record));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 方法列：HTTP 显示方法名，其余显示协议简称 */
    private static String protocolLabel(CaptureRecord record) {
        if (record.protocol == CaptureRecord.PROTOCOL_HTTP) {
            return record.method == null || record.method.isEmpty() ? "HTTP" : record.method;
        }
        if (record.protocol == CaptureRecord.PROTOCOL_HTTPS) {
            return "HTTPS";
        }
        if (record.protocol == CaptureRecord.PROTOCOL_UDP) {
            return "UDP";
        }
        return "TCP";
    }

    /** 状态列：HTTP 显示状态码，HTTPS/UDP 显示协议标记；失败使用浅红底 */
    private static void bindBadge(TextView badge, CaptureRecord record) {
        Context context = badge.getContext();
        String text;
        boolean error = record.failed;
        if (record.statusCode > 0) {
            text = String.valueOf(record.statusCode);
            if (record.statusCode >= 400) {
                error = true;
            }
        } else if (record.failed) {
            text = context.getString(R.string.capture_status_failed);
        } else if (record.protocol == CaptureRecord.PROTOCOL_HTTPS) {
            text = "HTTPS";
        } else if (record.protocol == CaptureRecord.PROTOCOL_UDP) {
            text = "UDP";
        } else {
            text = "TCP";
        }
        badge.setText(text);
        badge.setBackgroundResource(error ? R.drawable.bg_badge_high : R.drawable.bg_badge_normal);
        badge.setTextColor(context.getColor(error ? R.color.capture_error : R.color.menu_text_black));
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView methodText;
        final TextView targetText;
        final TextView statusBadge;
        final TextView metaText;
        final TextView noteText;

        Holder(@NonNull View itemView) {
            super(itemView);
            methodText = itemView.findViewById(R.id.methodText);
            targetText = itemView.findViewById(R.id.targetText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            metaText = itemView.findViewById(R.id.metaText);
            noteText = itemView.findViewById(R.id.noteText);
        }
    }
}
