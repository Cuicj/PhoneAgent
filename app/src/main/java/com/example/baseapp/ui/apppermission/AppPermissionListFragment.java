package com.example.baseapp.ui.apppermission;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.AppPermissionRepository;
import com.example.baseapp.data.InstalledApp;
import com.example.baseapp.data.PermissionCategory;
import com.example.baseapp.databinding.FragmentAppPermissionListBinding;
import com.example.baseapp.util.Formats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 应用权限总览列表：展示本机已安装应用，支持按应用名/包名搜索与权限类别筛选。
 */
public class AppPermissionListFragment extends Fragment {

    private FragmentAppPermissionListBinding binding;
    private AppPermissionRepository repository;
    private AppPermissionAdapter adapter;

    /** 全量应用数据，搜索与筛选在内存中完成 */
    private final List<InstalledApp> allApps = new ArrayList<>();

    /** 筛选 Chip 与权限类别的映射 */
    private final SparseArray<PermissionCategory> chipCategories = new SparseArray<>();

    private PermissionCategory filter;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAppPermissionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppPermissionRepository.getInstance(requireContext());
        adapter = new AppPermissionAdapter(this::openDetail);
        binding.appList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.appList.setAdapter(adapter);
        setupSearch();
        setupFilters();
        loadApps();
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_app_permission);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 关键字同时匹配应用名与包名
                query = s == null ? "" : s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });
    }

    private void setupFilters() {
        chipCategories.put(R.id.chipStorage, PermissionCategory.STORAGE);
        chipCategories.put(R.id.chipCamera, PermissionCategory.CAMERA);
        chipCategories.put(R.id.chipMicrophone, PermissionCategory.MICROPHONE);
        chipCategories.put(R.id.chipLocation, PermissionCategory.LOCATION);
        chipCategories.put(R.id.chipContacts, PermissionCategory.CONTACTS);
        chipCategories.put(R.id.chipPhoneSms, PermissionCategory.PHONE_SMS);
        chipCategories.put(R.id.chipCalendar, PermissionCategory.CALENDAR);
        chipCategories.put(R.id.chipSensors, PermissionCategory.SENSORS);
        chipCategories.put(R.id.chipNotification, PermissionCategory.NOTIFICATION);

        binding.filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            filter = checkedId == R.id.chipAll ? null : chipCategories.get(checkedId);
            applyFilter();
        });
    }

    private void loadApps() {
        binding.loading.setVisibility(View.VISIBLE);
        binding.emptyView.setVisibility(View.GONE);
        repository.loadApps(false, new AppPermissionRepository.Callback<List<InstalledApp>>() {
            @Override
            public void onSuccess(List<InstalledApp> apps) {
                if (binding == null) {
                    return;
                }
                allApps.clear();
                allApps.addAll(apps);
                binding.loading.setVisibility(View.GONE);
                applyFilter();
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

    /** 按当前关键字与类别筛选，并更新统计与空状态 */
    private void applyFilter() {
        if (binding == null) {
            return;
        }
        List<InstalledApp> result = new ArrayList<>();
        for (InstalledApp app : allApps) {
            if (filter != null && !app.hasCategory(filter)) {
                continue;
            }
            if (!query.isEmpty()
                    && !app.getLabel().toLowerCase(Locale.ROOT).contains(query)
                    && !app.getPackageName().toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            result.add(app);
        }
        adapter.submit(result);
        binding.countText.setText(getString(R.string.app_perm_count, result.size()));
        boolean empty = result.isEmpty();
        binding.emptyView.setText(R.string.app_perm_empty);
        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.appList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void openDetail(InstalledApp app) {
        Bundle args = new Bundle();
        args.putString(AppPermissionDetailFragment.ARG_PACKAGE_NAME, app.getPackageName());
        args.putString(AppPermissionDetailFragment.ARG_APP_LABEL, app.getLabel());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_appPermissionList_to_appPermissionDetail, args);
    }
}
