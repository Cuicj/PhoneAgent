package com.example.baseapp.ui.apppermission;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.baseapp.R;
import com.example.baseapp.data.AppPermissionDetail;
import com.example.baseapp.data.AppPermissionRepository;
import com.example.baseapp.data.PermissionGroup;
import com.example.baseapp.data.PermissionItem;
import com.example.baseapp.databinding.FragmentAppPermissionDetailBinding;
import com.example.baseapp.databinding.ItemPermissionEntryBinding;
import com.example.baseapp.databinding.ItemPermissionGroupBinding;
import com.example.baseapp.util.Formats;

/**
 * 应用权限详情：按类别分组展示声明的权限，并标注危险性与安装时授予状态。
 */
public class AppPermissionDetailFragment extends Fragment {

    public static final String ARG_PACKAGE_NAME = "packageName";
    public static final String ARG_APP_LABEL = "appLabel";

    private FragmentAppPermissionDetailBinding binding;
    private AppPermissionRepository repository;
    private String appLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAppPermissionDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppPermissionRepository.getInstance(requireContext());
        Bundle args = requireArguments();
        String packageName = args.getString(ARG_PACKAGE_NAME);
        appLabel = args.getString(ARG_APP_LABEL);
        if (packageName == null) {
            binding.loading.setVisibility(View.GONE);
            binding.emptyView.setText(R.string.app_perm_missing);
            binding.emptyView.setVisibility(View.VISIBLE);
            return;
        }
        binding.appPackage.setText(packageName);
        if (appLabel != null) {
            binding.appLabel.setText(appLabel);
        }
        AppIconLoader.load(binding.appIcon, packageName);
        loadDetail(packageName);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (appLabel != null && !appLabel.isEmpty()) {
            requireActivity().setTitle(appLabel);
        } else {
            requireActivity().setTitle(R.string.title_app_permission);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadDetail(String packageName) {
        binding.loading.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        repository.loadDetail(packageName, new AppPermissionRepository.Callback<AppPermissionDetail>() {
            @Override
            public void onSuccess(AppPermissionDetail detail) {
                if (binding == null) {
                    return;
                }
                binding.loading.setVisibility(View.GONE);
                bindDetail(detail);
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (binding == null) {
                    return;
                }
                binding.loading.setVisibility(View.GONE);
                binding.emptyView.setText(getString(R.string.app_perm_error,
                        Formats.errorMessage(error)));
                binding.emptyView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void bindDetail(AppPermissionDetail detail) {
        binding.appLabel.setText(detail.getLabel());
        binding.appPackage.setText(detail.getPackageName());
        appLabel = detail.getLabel();
        requireActivity().setTitle(detail.getLabel());

        // 版本与系统/用户应用标识
        String version = detail.getVersionName() == null ? ""
                : getString(R.string.app_perm_detail_version, detail.getVersionName());
        String type = getString(detail.isSystem()
                ? R.string.app_perm_detail_system : R.string.app_perm_detail_user);
        binding.appMeta.setText(version.isEmpty() ? type : version + " · " + type);
        binding.appSummary.setText(getString(R.string.app_perm_detail_summary,
                detail.getDeclaredCount(), detail.getDangerousCount(),
                detail.getInstallGrantedCount()));
        AppIconLoader.load(binding.appIcon, detail.getPackageName());

        binding.permissionSections.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (PermissionGroup group : detail.getGroups()) {
            // 分组标题
            ItemPermissionGroupBinding groupBinding = ItemPermissionGroupBinding.inflate(
                    inflater, binding.permissionSections, false);
            groupBinding.groupTitle.setText(group.getCategory().getTitleRes());
            groupBinding.groupCount.setText(getString(R.string.perm_count, group.getItems().size()));
            binding.permissionSections.addView(groupBinding.getRoot());

            // 分组内的权限条目
            for (PermissionItem item : group.getItems()) {
                ItemPermissionEntryBinding entryBinding = ItemPermissionEntryBinding.inflate(
                        inflater, binding.permissionSections, false);
                entryBinding.permLabel.setText(item.getLabel());
                entryBinding.permName.setText(item.getName());
                boolean hasDescription = item.getDescription() != null
                        && !item.getDescription().isEmpty();
                entryBinding.permDescription.setText(
                        hasDescription ? item.getDescription() : null);
                entryBinding.permDescription.setVisibility(
                        hasDescription ? View.VISIBLE : View.GONE);
                bindLevelBadge(entryBinding.permLevelBadge, item.isDangerous());
                bindGrantBadge(entryBinding.permGrantBadge, item.isInstallGranted());
                binding.permissionSections.addView(entryBinding.getRoot());
            }
        }
        binding.emptyView.setVisibility(
                detail.getGroups().isEmpty() ? View.VISIBLE : View.GONE);
    }

    /** 危险权限用浅红标签，普通权限用中性灰标签 */
    private void bindLevelBadge(TextView badge, boolean dangerous) {
        badge.setText(dangerous ? R.string.perm_badge_dangerous : R.string.perm_badge_normal);
        applyBadge(badge,
                dangerous ? R.drawable.bg_badge_dangerous : R.drawable.bg_badge_normal,
                dangerous ? R.color.op_on_delete_container : R.color.on_access_container);
    }

    /** 安装时授予用浅蓝标签，运行时权限用暖色标签提示状态未知 */
    private void bindGrantBadge(TextView badge, boolean installGranted) {
        badge.setText(installGranted
                ? R.string.perm_install_granted : R.string.perm_runtime_required);
        applyBadge(badge,
                installGranted ? R.drawable.bg_badge_granted : R.drawable.bg_badge_pending,
                installGranted
                        ? R.color.brand_on_primary_container : R.color.op_on_revert_container);
    }

    private void applyBadge(TextView badge, @DrawableRes int background,
                            @ColorRes int textColor) {
        badge.setBackgroundResource(background);
        badge.setTextColor(ContextCompat.getColor(requireContext(), textColor));
    }
}
