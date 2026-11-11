package com.example.baseapp.ui.fileversion;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.baseapp.R;
import com.example.baseapp.data.FileSummary;
import com.example.baseapp.data.FileVersionRepository;
import com.example.baseapp.data.FileVersionRepository.LibrarySnapshot;
import com.example.baseapp.databinding.FragmentFileLibraryBinding;
import com.example.baseapp.util.Formats;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 文件列表页：展示已记录的文件、版本数量与本地占用，并支持导入原始图片。
 */
public class FileLibraryFragment extends Fragment {

    private FragmentFileLibraryBinding binding;
    private FileVersionRepository repository;
    private FileRecordAdapter adapter;
    private boolean importing;

    /** 选择一张图片作为新文件的原始版本 */
    private final ActivityResultLauncher<String[]> importLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    importImage(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFileLibraryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = FileVersionRepository.getInstance(requireContext());
        adapter = new FileRecordAdapter(new FileRecordAdapter.Listener() {
            @Override
            public void onItemClick(FileSummary item) {
                openHistory(item);
            }

            @Override
            public void onItemLongClick(FileSummary item) {
                confirmDeleteFile(item);
            }
        });
        binding.fileList.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.fileList.setAdapter(adapter);
        binding.importButton.setOnClickListener(button -> {
            if (!importing) {
                // 只挑选图片，复制后立刻落盘，不依赖原图后续是否被修改
                importLauncher.launch(new String[]{"image/*"});
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(R.string.title_files);
        loadLibrary();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void loadLibrary() {
        repository.loadLibrary(new FileVersionRepository.Callback<LibrarySnapshot>() {
            @Override
            public void onSuccess(LibrarySnapshot snapshot) {
                if (binding == null) {
                    return;
                }
                adapter.submit(snapshot.getFiles());
                boolean empty = snapshot.getFiles().isEmpty();
                binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.fileList.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.statsText.setText(getString(R.string.files_storage_usage,
                        snapshot.getFiles().size(),
                        Formats.formatBytes(snapshot.getTotalBytes())));
            }
        });
    }

    private void importImage(Uri uri) {
        setImporting(true);
        repository.importImage(uri, new FileVersionRepository.Callback<Long>() {
            @Override
            public void onSuccess(Long value) {
                setImporting(false);
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(), R.string.import_original_success,
                        Toast.LENGTH_SHORT).show();
                loadLibrary();
            }

            @Override
            public void onError(@NonNull Exception error) {
                setImporting(false);
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(),
                        getString(R.string.import_failed, Formats.errorMessage(error)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setImporting(boolean value) {
        importing = value;
        if (binding == null) {
            return;
        }
        binding.importButton.setEnabled(!value);
        binding.importButton.setText(value ? R.string.importing : R.string.files_import);
    }

    private void openHistory(FileSummary item) {
        Bundle args = new Bundle();
        args.putLong(FileHistoryFragment.ARG_FILE_ID, item.getId());
        args.putString(FileHistoryFragment.ARG_FILE_NAME, item.getName());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_navigation_files_to_fileHistory, args);
    }

    private void confirmDeleteFile(FileSummary item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_file_title)
                .setMessage(getString(R.string.dialog_delete_file_message, item.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteFile(item))
                .show();
    }

    private void deleteFile(FileSummary item) {
        repository.deleteFile(item.getId(), new FileVersionRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(), R.string.delete_success,
                        Toast.LENGTH_SHORT).show();
                loadLibrary();
            }

            @Override
            public void onError(@NonNull Exception error) {
                if (!isAdded() || binding == null) {
                    return;
                }
                Toast.makeText(requireContext(),
                        getString(R.string.delete_failed, Formats.errorMessage(error)),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
