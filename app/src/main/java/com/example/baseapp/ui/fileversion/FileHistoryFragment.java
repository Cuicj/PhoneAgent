package com.example.baseapp.ui.fileversion;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.baseapp.R;
import com.example.baseapp.data.FileAccessProvider;
import com.example.baseapp.data.FileAccessRepository;
import com.example.baseapp.data.FileAppGrant;
import com.example.baseapp.data.FileVersionEntry;
import com.example.baseapp.data.FileVersionRepository;
import com.example.baseapp.data.TimelineItem;
import com.example.baseapp.databinding.FragmentFileHistoryBinding;
import com.example.baseapp.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * 版本历史页：按 Git 提交时间线展示版本操作与外部访问事件，并管理“可查看的应用”。
 */
public class FileHistoryFragment extends Fragment {

    /** 文件主键参数名 */
    public static final String ARG_FILE_ID = "fileId";

    /** 文件名称参数名 */
    public static final String ARG_FILE_NAME = "fileName";

    private FragmentFileHistoryBinding binding;
    private FileVersionRepository repository;
    private FileAccessRepository accessRepository;
    private FileVersionAdapter adapter;
    private AppGrantAdapter grantAdapter;
    private long fileId = -1L;
    private String fileName = "";
    private boolean addingVersion;
    private boolean scrollToLatest = true;
    /** 待回收临时读取授权的目标包名 */
    private String pendingGrantPackage;
    /** 待回收临时读取授权的文件 URI */
    private Uri pendingGrantUri;

    /** 选择修改后的图片，追加为新的版本 */
    private final ActivityResultLauncher<String[]> addVersionLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    addVersion(uri);
                }
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            fileId = args.getLong(ARG_FILE_ID, -1L);
            fileName = args.getString(ARG_FILE_NAME, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileHistoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (fileId <= 0) {
            Toast.makeText(requireContext(), R.string.file_record_missing, Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStack();
            return;
        }
        repository = FileVersionRepository.getInstance(requireContext());
        accessRepository = FileAccessRepository.getInstance(requireContext());

        adapter = new FileVersionAdapter(new FileVersionAdapter.Listener() {
            @Override
            public void onItemClick(FileVersionEntry item) {
                showPreview(item);
            }

            @Override
            public void onItemLongClick(FileVersionEntry item) {
                showVersionActions(item);
            }
        });
        binding.versionList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.versionList.setAdapter(adapter);

        // 可查看的应用：横向授权列表
        grantAdapter = new AppGrantAdapter(this::confirmRevoke);
        binding.grantList.setLayoutManager(new LinearLayoutManager(
                requireContext(), RecyclerView.HORIZONTAL, false));
        binding.grantList.setAdapter(grantAdapter);
        binding.grantAddButton.setOnClickListener(button -> showGrantPicker());

        binding.addVersionButton.setOnClickListener(button -> {
            if (!addingVersion) {
                addVersionLauncher.launch(new String[]{"image/*"});
            }
        });
        binding.openWithButton.setOnClickListener(button -> showOpenWith());
    }

    @Override
    public void onResume() {
        super.onResume();
        // 从外部应用返回时回收临时读取授权，避免权限长期留存
        revokePendingGrant();
        requireActivity().setTitle(fileName.isEmpty()
                ? getString(R.string.history_title) : fileName);
        if (fileId > 0) {
            loadTimeline();
            loadGrants();
        }
    }

    @Override
    public void onDestroyView() {
        // 页面销毁时兜底回收，防止未返回页面就退出造成权限泄漏
        revokePendingGrant();
        super.onDestroyView();
        binding = null;
    }

    /** 加载版本与访问事件合并后的时间线 */
    private void loadTimeline() {
        repository.loadTimeline(fileId,
                new FileVersionRepository.Callback<List<TimelineItem>>() {
                    @Override
                    public void onSuccess(List<TimelineItem> items) {
                        if (binding == null) {
                            return;
                        }
                        adapter.submit(items);
                        boolean empty = items.isEmpty();
                        binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                        binding.versionList.setVisibility(empty ? View.GONE : View.VISIBLE);
                        binding.summaryText.setText(getString(R.string.history_summary,
                                items.size()));
                        if (scrollToLatest && !empty) {
                            scrollToLatest = false;
                            // 整页滚动后，直接滚到最底部以展示最新记录
                            binding.scrollRoot.post(() -> {
                                if (binding != null) {
                                    binding.scrollRoot.fullScroll(View.FOCUS_DOWN);
                                }
                            });
                        }
                    }
                });
    }

    /** 加载已授权的应用列表 */
    private void loadGrants() {
        accessRepository.loadGrants(fileId,
                new FileVersionRepository.Callback<List<FileAppGrant>>() {
                    @Override
                    public void onSuccess(List<FileAppGrant> grants) {
                        if (binding == null) {
                            return;
                        }
                        grantAdapter.submit(grants);
                        boolean empty = grants.isEmpty();
                        binding.grantEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                        binding.grantList.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }
                });
    }

    /** 选择已授权的外部应用打开当前版本 */
    private void showOpenWith() {
        accessRepository.loadAuthorizedOpenTargets(fileId,
                new FileVersionRepository.Callback<List<FileAccessRepository.GrantableApp>>() {
                    @Override
                    public void onSuccess(List<FileAccessRepository.GrantableApp> apps) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        if (apps.isEmpty()) {
                            showOpenWithEmpty();
                            return;
                        }
                        String[] labels = new String[apps.size()];
                        for (int i = 0; i < apps.size(); i++) {
                            labels[i] = apps.get(i).getLabel();
                        }
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.open_with_title)
                                .setItems(labels, (dialog, which) -> openWithApp(apps.get(which)))
                                .setNegativeButton(R.string.action_cancel, null)
                                .show();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(), R.string.open_with_failed,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** 没有已授权应用时给出空状态，并引导先授权 */
    private void showOpenWithEmpty() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.open_with_title)
                .setMessage(R.string.open_with_empty)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.open_with_grant,
                        (dialog, which) -> showGrantPicker())
                .show();
    }

    /** 以 content:// URI 交给目标应用打开，并授予临时读取权限 */
    private void openWithApp(FileAccessRepository.GrantableApp app) {
        if (!isAdded() || binding == null) {
            return;
        }
        // 没有有效版本时无可打开的文件
        if (!adapter.hasActiveVersion()) {
            Toast.makeText(requireContext(), R.string.open_with_no_version,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileAccessProvider.buildUri(fileId);
        String mimeType = adapter.getLatestMimeType();
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri,
                mimeType == null || mimeType.isEmpty() ? "image/*" : mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // 显式指定目标包名，确保调用方包名解析落在该应用上
        intent.setPackage(app.getPackageName());
        try {
            requireContext().grantUriPermission(app.getPackageName(), uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            pendingGrantPackage = app.getPackageName();
            pendingGrantUri = uri;
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            // 启动失败立即回收，避免授权悬挂
            revokePendingGrant();
            Toast.makeText(requireContext(), R.string.open_with_failed,
                    Toast.LENGTH_LONG).show();
        }
    }

    /** 回收上一步授予目标应用的临时读取权限 */
    private void revokePendingGrant() {
        String packageName = pendingGrantPackage;
        Uri uri = pendingGrantUri;
        pendingGrantPackage = null;
        pendingGrantUri = null;
        if (packageName == null || uri == null) {
            return;
        }
        Context context = getContext();
        if (context == null) {
            return;
        }
        try {
            context.revokeUriPermission(packageName, uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (RuntimeException ignored) {
            // 目标应用已卸载等异常场景无需阻断页面
        }
    }

    private void addVersion(Uri uri) {
        setAddingVersion(true);
        repository.addVersion(fileId, uri, new FileVersionRepository.Callback<Integer>() {
            @Override
            public void onSuccess(Integer versionNo) {
                setAddingVersion(false);
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(),
                        getString(R.string.version_added_success, versionNo),
                        Toast.LENGTH_SHORT).show();
                scrollToLatest = true;
                loadTimeline();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setAddingVersion(false);
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(),
                        getString(R.string.import_failed, Formats.errorMessage(error)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setAddingVersion(boolean value) {
        addingVersion = value;
        if (binding == null) {
            return;
        }
        binding.addVersionButton.setEnabled(!value);
        binding.addVersionButton.setText(value
                ? R.string.importing : R.string.history_add_version);
    }

    private void showPreview(FileVersionEntry item) {
        if (!isAdded()) {
            return;
        }
        // 已删除版本没有图片副本，只提示不打开大图
        if (item.isDeleted()) {
            Toast.makeText(requireContext(), R.string.deleted_version_tip,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // 使用全屏黑色对话框查看原图，避免依赖外部图片应用
        Dialog dialog = new Dialog(requireContext(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_image_preview, null, false);
        ImageView previewImage = content.findViewById(R.id.previewImage);
        content.setOnClickListener(view -> dialog.dismiss());
        ThumbnailLoader.loadPreview(previewImage,
                repository.resolvePath(item.getImagePath()));
        dialog.setContentView(content);
        dialog.show();
    }

    /**
     * 长按版本：已删除条目只提示；否则按可执行的操作动态生成菜单。
     */
    private void showVersionActions(FileVersionEntry item) {
        if (!isAdded() || binding == null) {
            return;
        }
        if (item.isDeleted()) {
            Toast.makeText(requireContext(), R.string.deleted_version_tip,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        // 当前最新有效版本无需回退；原始版本不允许删除
        final boolean canRevert = !adapter.isLatestActive(item);
        final boolean canDelete = item.getVersionNo() > 1;
        if (!canRevert && !canDelete) {
            Toast.makeText(requireContext(), R.string.delete_original_blocked,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> actions = new ArrayList<>();
        if (canRevert) {
            actions.add(getString(R.string.action_revert));
        }
        if (canDelete) {
            actions.add(getString(R.string.action_delete_version));
        }
        final int deleteIndex = canRevert ? 1 : 0;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.version_actions_title, item.getVersionNo()))
                .setItems(actions.toArray(new String[0]), (dialog, which) -> {
                    if (which == deleteIndex) {
                        confirmDeleteVersion(item);
                    } else {
                        revertToVersion(item.getVersionNo());
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** 删除版本前二次确认，说明只移除图片副本并保留时间线记录 */
    private void confirmDeleteVersion(FileVersionEntry item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_version_title)
                .setMessage(getString(R.string.dialog_delete_version_message,
                        item.getVersionNo()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete,
                        (dialog, which) -> deleteVersion(item.getVersionNo()))
                .show();
    }

    private void revertToVersion(int versionNo) {
        repository.revertToVersion(fileId, versionNo,
                new FileVersionRepository.Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer newVersionNo) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.revert_success, newVersionNo),
                                Toast.LENGTH_SHORT).show();
                        scrollToLatest = true;
                        loadTimeline();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.action_failed,
                                        Formats.errorMessage(error)),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void deleteVersion(int versionNo) {
        repository.deleteVersion(fileId, versionNo,
                new FileVersionRepository.Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer latestVersionNo) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.delete_version_success, versionNo),
                                Toast.LENGTH_SHORT).show();
                        scrollToLatest = true;
                        loadTimeline();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.action_failed,
                                        Formats.errorMessage(error)),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** 选择要授权查看该文件的外部应用 */
    private void showGrantPicker() {
        accessRepository.loadGrantableApps(fileId,
                new FileVersionRepository.Callback<List<FileAccessRepository.GrantableApp>>() {
                    @Override
                    public void onSuccess(List<FileAccessRepository.GrantableApp> apps) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        if (apps.isEmpty()) {
                            Toast.makeText(requireContext(), R.string.grant_no_candidates,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String[] labels = new String[apps.size()];
                        for (int i = 0; i < apps.size(); i++) {
                            labels[i] = apps.get(i).getLabel();
                        }
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.grant_choose_title)
                                .setItems(labels, (dialog, which) -> grantApp(apps.get(which)))
                                .setNegativeButton(R.string.action_cancel, null)
                                .show();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.grant_failed,
                                        Formats.errorMessage(error)),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void grantApp(FileAccessRepository.GrantableApp app) {
        accessRepository.grant(fileId, app,
                new FileVersionRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.grant_success, app.getLabel()),
                                Toast.LENGTH_SHORT).show();
                        loadGrants();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.grant_failed,
                                        Formats.errorMessage(error)),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** 取消授权前二次确认 */
    private void confirmRevoke(FileAppGrant grant) {
        if (!isAdded() || binding == null) {
            return;
        }
        String label = grant.getAppLabel();
        if (label == null || label.trim().isEmpty()) {
            label = grant.getPackageName();
        }
        final String appLabel = label;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_revoke_title)
                .setMessage(getString(R.string.dialog_revoke_message, appLabel))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_revoke_grant,
                        (dialog, which) -> revokeApp(grant.getPackageName(), appLabel))
                .show();
    }

    private void revokeApp(String packageName, String appLabel) {
        accessRepository.revoke(fileId, packageName,
                new FileVersionRepository.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.grant_revoke_success, appLabel),
                                Toast.LENGTH_SHORT).show();
                        loadGrants();
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        if (!isAdded() || binding == null) {
                            return;
                        }
                        Toast.makeText(requireContext(),
                                getString(R.string.grant_failed,
                                        Formats.errorMessage(error)),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
