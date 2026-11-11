package com.example.baseapp.ui.apppermission;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.InstalledApp;
import com.example.baseapp.databinding.ItemAppPermissionBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用列表适配器：展示图标、名称、包名与权限数量，点击进入权限详情。
 */
public class AppPermissionAdapter extends RecyclerView.Adapter<AppPermissionAdapter.Holder> {

    public interface Listener {
        void onAppClick(InstalledApp app);
    }

    private final List<InstalledApp> items = new ArrayList<>();
    private final Listener listener;

    public AppPermissionAdapter(Listener listener) {
        this.listener = listener;
    }

    /** 替换列表数据；由 Fragment 在主线程调用 */
    public void submit(List<InstalledApp> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppPermissionBinding binding = ItemAppPermissionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        InstalledApp item = items.get(position);
        holder.binding.appLabel.setText(item.getLabel());
        holder.binding.appPackage.setText(item.getPackageName());
        holder.binding.appSummary.setText(holder.itemView.getContext().getString(
                R.string.app_perm_item_summary, item.getDeclaredCount(), item.getDangerousCount()));
        AppIconLoader.load(holder.binding.appIcon, item.getPackageName());
        holder.itemView.setOnClickListener(view -> listener.onAppClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemAppPermissionBinding binding;

        Holder(ItemAppPermissionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
