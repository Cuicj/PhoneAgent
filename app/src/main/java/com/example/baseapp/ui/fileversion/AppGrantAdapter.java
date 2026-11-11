package com.example.baseapp.ui.fileversion;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.FileAppGrant;
import com.example.baseapp.databinding.ItemAppGrantBinding;
import com.example.baseapp.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * “可查看的应用”授权列表：展示应用名称、授权时间，并提供取消授权入口。
 */
public class AppGrantAdapter extends RecyclerView.Adapter<AppGrantAdapter.Holder> {

    public interface Listener {
        void onRevoke(FileAppGrant grant);
    }

    private final List<FileAppGrant> items = new ArrayList<>();
    private final Listener listener;

    public AppGrantAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FileAppGrant> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppGrantBinding binding = ItemAppGrantBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FileAppGrant item = items.get(position);
        String label = item.getAppLabel();
        if (label == null || label.trim().isEmpty()) {
            label = item.getPackageName();
        }
        holder.binding.appGrantLabel.setText(label);
        holder.binding.appGrantTime.setText(holder.itemView.getContext().getString(
                R.string.grant_time, Formats.formatTime(item.getGrantedAt())));
        holder.binding.appGrantRevoke.setOnClickListener(
                view -> listener.onRevoke(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemAppGrantBinding binding;

        Holder(ItemAppGrantBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
