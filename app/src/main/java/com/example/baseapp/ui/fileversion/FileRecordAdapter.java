package com.example.baseapp.ui.fileversion;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.databinding.ItemFileRecordBinding;
import com.example.baseapp.data.FileSummary;
import com.example.baseapp.data.FileVersionRepository;
import com.example.baseapp.util.Formats;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件列表适配器：展示名称、版本数量、最近记录时间与最新版缩略图。
 */
public class FileRecordAdapter extends RecyclerView.Adapter<FileRecordAdapter.Holder> {

    public interface Listener {
        void onItemClick(FileSummary item);

        void onItemLongClick(FileSummary item);
    }

    private final List<FileSummary> items = new ArrayList<>();
    private final Listener listener;

    public FileRecordAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<FileSummary> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemFileRecordBinding binding = ItemFileRecordBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FileSummary item = items.get(position);
        String countText = holder.binding.getRoot().getContext().getString(
                R.string.file_versions_count, item.getVersionCount());
        String timeText = holder.binding.getRoot().getContext().getString(
                R.string.file_latest_time, Formats.formatTime(item.getUpdatedAt()));

        holder.binding.nameText.setText(item.getName());
        holder.binding.countText.setText(countText);
        holder.binding.timeText.setText(timeText);

        String thumbPath = FileVersionRepository.getInstance(
                holder.binding.getRoot().getContext()).resolvePath(item.getLatestThumbPath());
        ThumbnailLoader.load(holder.binding.thumbnail, thumbPath);

        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> {
            listener.onItemLongClick(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemFileRecordBinding binding;

        Holder(ItemFileRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
