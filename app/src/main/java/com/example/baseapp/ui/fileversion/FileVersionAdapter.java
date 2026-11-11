package com.example.baseapp.ui.fileversion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.FileAccessEvent;
import com.example.baseapp.data.FileAccessType;
import com.example.baseapp.data.FileVersionEntry;
import com.example.baseapp.data.FileVersionRepository;
import com.example.baseapp.data.TimelineItem;
import com.example.baseapp.data.VersionOperationType;
import com.example.baseapp.databinding.ItemFileAccessEventBinding;
import com.example.baseapp.databinding.ItemFileVersionBinding;
import com.example.baseapp.util.Formats;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;
import java.util.List;

/**
 * 时间线适配器：版本记录按 Git 提交卡片展示，外部访问事件用更轻量的节点展示。
 */
public class FileVersionAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onItemClick(FileVersionEntry item);

        void onItemLongClick(FileVersionEntry item);
    }

    private final List<TimelineItem> items = new ArrayList<>();
    private final Listener listener;
    private int latestActiveVersionNo;
    private String latestMimeType;

    public FileVersionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<TimelineItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        latestActiveVersionNo = 0;
        latestMimeType = null;
        for (TimelineItem item : items) {
            FileVersionEntry version = item.getVersion();
            if (version != null && !version.isDeleted()
                    && version.getVersionNo() > latestActiveVersionNo) {
                latestActiveVersionNo = version.getVersionNo();
                latestMimeType = version.getMimeType();
            }
        }
        notifyDataSetChanged();
    }

    /** 当前最新有效版本的 MIME，供“用其他应用打开”构造 Intent */
    public String getLatestMimeType() {
        return latestMimeType;
    }

    /** 是否存在可对外打开的有效版本 */
    public boolean hasActiveVersion() {
        return latestActiveVersionNo > 0;
    }

    /** 该条目是否为当前最新有效版本 */
    public boolean isLatestActive(FileVersionEntry item) {
        return item != null && !item.isDeleted() && item.getVersionNo() == latestActiveVersionNo;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TimelineItem.TYPE_ACCESS) {
            return new AccessHolder(ItemFileAccessEventBinding.inflate(inflater, parent, false));
        }
        return new VersionHolder(ItemFileVersionBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TimelineItem timelineItem = items.get(position);
        if (timelineItem.getType() == TimelineItem.TYPE_ACCESS
                && holder instanceof AccessHolder) {
            bindAccess((AccessHolder) holder, timelineItem.getAccess(), position);
        } else if (holder instanceof VersionHolder) {
            bindVersion((VersionHolder) holder, timelineItem.getVersion(), position);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** 绑定版本记录：Git 提交卡片 */
    private void bindVersion(VersionHolder holder, FileVersionEntry item, int position) {
        Context context = holder.itemView.getContext();
        boolean deleted = item.isDeleted();
        boolean isLatest = isLatestActive(item);

        holder.itemView.setAlpha(deleted ? 0.65f : 1f);

        // 操作类型标签
        VersionOperationType operationType = item.getOperationType();
        holder.binding.operationBadge.setText(operationLabelRes(operationType));
        holder.binding.operationBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, operationColorRes(operationType))));
        holder.binding.operationBadge.setTextColor(ContextCompat.getColor(context,
                operationTextColorRes(operationType)));
        holder.binding.versionText.setText(context.getString(
                R.string.version_label, item.getVersionNo()));

        // 原始 / 最新标识
        boolean showOriginal = !deleted && item.getVersionNo() == 1;
        holder.binding.originalFlag.setVisibility(showOriginal ? View.VISIBLE : View.GONE);
        holder.binding.latestFlag.setVisibility(isLatest ? View.VISIBLE : View.GONE);
        holder.binding.flagsRow.setVisibility(showOriginal || isLatest
                ? View.VISIBLE : View.GONE);

        // 自动生成的描述正文
        holder.binding.descriptionText.setText(item.getDescription());
        holder.binding.timeText.setText(Formats.formatTime(item.getCreatedAt()));
        holder.binding.metaText.setText(buildMeta(item));

        // 已删除条目只保留元信息，图片位置展示占位
        holder.binding.versionImage.setVisibility(deleted ? View.GONE : View.VISIBLE);
        holder.binding.deletedPlaceholder.setVisibility(deleted ? View.VISIBLE : View.GONE);
        if (deleted) {
            holder.binding.versionImage.setImageDrawable(null);
        } else {
            String imagePath = FileVersionRepository.getInstance(context).resolvePath(
                    item.getThumbPath() != null ? item.getThumbPath() : item.getImagePath());
            ThumbnailLoader.load(holder.binding.versionImage, imagePath);
        }

        // 时间线节点：删除使用灰色，最新版本节点放大
        int dotColorRes = deleted ? R.color.timeline_deleted
                : operationTextColorRes(operationType);
        holder.binding.timelineDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, dotColorRes)));
        float dotScale = isLatest ? 1.4f : 1f;
        holder.binding.timelineDot.setScaleX(dotScale);
        holder.binding.timelineDot.setScaleY(dotScale);
        holder.binding.timelineLine.setVisibility(
                position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        // 最新版本卡片描边加粗，形成明显的提交节点强调
        int primary = MaterialColors.getColor(holder.itemView,
                androidx.appcompat.R.attr.colorPrimary);
        int outline = MaterialColors.getColor(holder.itemView,
                com.google.android.material.R.attr.colorOutlineVariant);
        holder.binding.versionCard.setStrokeColor(isLatest ? primary : outline);
        holder.binding.versionCard.setStrokeWidth(dp(context, isLatest ? 2 : 1));

        holder.binding.versionCard.setOnClickListener(
                view -> listener.onItemClick(item));
        holder.binding.versionCard.setOnLongClickListener(view -> {
            listener.onItemLongClick(item);
            return true;
        });
    }

    /** 绑定访问事件：空心节点 + 单行文案，与版本卡片区分 */
    private void bindAccess(AccessHolder holder, FileAccessEvent access, int position) {
        Context context = holder.itemView.getContext();
        FileAccessType type = access.getAccessType();

        holder.binding.accessBadge.setText(type.getLabel());
        holder.binding.accessBadge.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, accessColorRes(type))));
        holder.binding.accessBadge.setTextColor(ContextCompat.getColor(context,
                accessTextColorRes(type)));
        holder.binding.accessText.setText(describeAccess(context, access));
        holder.binding.accessTime.setText(Formats.formatTime(access.getOccurredAt()));
        holder.binding.accessDot.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, accessDotColorRes(type))));
        holder.binding.timelineLine.setVisibility(
                position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        // 访问事件只做展示，不提供回退/删除等版本操作
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
    }

    /** 访问事件的自动文案：调用应用 + 操作 + 版本 */
    private String describeAccess(Context context, FileAccessEvent access) {
        String label = access.getCallerLabel();
        if (label == null || label.trim().isEmpty()) {
            label = access.getCallerPackage();
        }
        if (label == null || label.trim().isEmpty()) {
            label = context.getString(R.string.access_event_unknown_app);
        }
        if (access.getAccessType() == FileAccessType.BLOCKED) {
            return context.getString(R.string.access_event_blocked, label);
        }
        if (access.getAccessType() == FileAccessType.WRITE) {
            return context.getString(R.string.access_event_write, label);
        }
        if (access.getVersionNo() > 0) {
            return context.getString(R.string.access_event_read_version, label,
                    access.getVersionNo());
        }
        return context.getString(R.string.access_event_read, label);
    }

    /** 关键元信息：体积 · 尺寸 · 格式 */
    private String buildMeta(FileVersionEntry item) {
        StringBuilder builder = new StringBuilder(Formats.formatBytes(item.getSizeBytes()));
        if (item.getWidth() > 0 && item.getHeight() > 0) {
            builder.append(" · ").append(item.getWidth()).append("×").append(item.getHeight());
        }
        builder.append(" · ").append(Formats.formatMime(item.getMimeType()));
        return builder.toString();
    }

    private int operationLabelRes(VersionOperationType type) {
        if (type == VersionOperationType.ORIGINAL_IMPORT) {
            return R.string.op_original_import;
        }
        if (type == VersionOperationType.REVERT) {
            return R.string.op_revert;
        }
        if (type == VersionOperationType.DELETE_VERSION) {
            return R.string.op_delete_version;
        }
        return R.string.op_add_version;
    }

    private int operationColorRes(VersionOperationType type) {
        if (type == VersionOperationType.ORIGINAL_IMPORT) {
            return R.color.brand_primary_container;
        }
        if (type == VersionOperationType.REVERT) {
            return R.color.op_revert_container;
        }
        if (type == VersionOperationType.DELETE_VERSION) {
            return R.color.op_delete_container;
        }
        return R.color.brand_secondary_container;
    }

    private int operationTextColorRes(VersionOperationType type) {
        if (type == VersionOperationType.ORIGINAL_IMPORT) {
            return R.color.brand_on_primary_container;
        }
        if (type == VersionOperationType.REVERT) {
            return R.color.op_on_revert_container;
        }
        if (type == VersionOperationType.DELETE_VERSION) {
            return R.color.op_on_delete_container;
        }
        return R.color.brand_on_secondary_container;
    }

    private int accessColorRes(FileAccessType type) {
        if (type == FileAccessType.BLOCKED) {
            return R.color.op_delete_container;
        }
        if (type == FileAccessType.WRITE) {
            return R.color.brand_primary_container;
        }
        return R.color.access_container;
    }

    private int accessTextColorRes(FileAccessType type) {
        if (type == FileAccessType.BLOCKED) {
            return R.color.op_on_delete_container;
        }
        if (type == FileAccessType.WRITE) {
            return R.color.brand_on_primary_container;
        }
        return R.color.on_access_container;
    }

    private int accessDotColorRes(FileAccessType type) {
        return type == FileAccessType.BLOCKED
                ? R.color.op_on_delete_container : R.color.on_access_container;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static class VersionHolder extends RecyclerView.ViewHolder {
        final ItemFileVersionBinding binding;

        VersionHolder(ItemFileVersionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static class AccessHolder extends RecyclerView.ViewHolder {
        final ItemFileAccessEventBinding binding;

        AccessHolder(ItemFileAccessEventBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
