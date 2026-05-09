package com.waenhancer.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.waenhancer.R;
import com.waenhancer.adapter.RecordingsAdapter;
import com.waenhancer.databinding.FragmentRecordingsBinding;
import com.waenhancer.model.Recording;
import com.waenhancer.ui.dialogs.AudioPlayerDialog;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RecordingsFragment extends Fragment implements RecordingsAdapter.OnRecordingActionListener {

    private FragmentRecordingsBinding binding;
    private RecordingsAdapter adapter;
    private final List<Recording> allRecordings = new ArrayList<>();
    private final List<File> baseDirs = new ArrayList<>();
    private int currentSortType = 1;
    private ActionMode actionMode;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRecordingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new RecordingsAdapter(this);
        binding.recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerView.setAdapter(adapter);

        adapter.setSelectionChangeListener(count -> {
            if (count > 0) {
                if (actionMode == null) {
                    actionMode = ((AppCompatActivity) requireActivity()).startSupportActionMode(actionModeCallback);
                }
                actionMode.setTitle(getString(R.string.selected_count, count));
            } else if (actionMode != null) {
                actionMode.finish();
            }
        });

        binding.swipeRefresh.setOnRefreshListener(() -> {
            initializeBaseDirs();
            loadRecordings();
        });

        requireActivity().addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_recordings, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.action_sort_date) {
                    currentSortType = 1;
                    loadRecordings();
                    return true;
                } else if (itemId == R.id.action_sort_name) {
                    currentSortType = 2;
                    loadRecordings();
                    return true;
                } else if (itemId == R.id.action_sort_duration) {
                    currentSortType = 3;
                    loadRecordings();
                    return true;
                } else if (itemId == R.id.action_sort_contact) {
                    currentSortType = 4;
                    loadRecordings();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        initializeBaseDirs();
        loadRecordings();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding == null) {
            return;
        }
        initializeBaseDirs();
        loadRecordings();
    }

    private void initializeBaseDirs() {
        String configuredPath = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString("call_recording_path", null);

        baseDirs.clear();
        Set<String> addedPaths = new LinkedHashSet<>();

        addBaseDir(addedPaths, new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "WA Call Recordings"
        ));

        if (configuredPath != null && !configuredPath.isEmpty()) {
            addBaseDir(addedPaths, new File(configuredPath, "WA Call Recordings"));
        }

        addBaseDir(addedPaths, new File(Environment.getExternalStorageDirectory(), "WA Call Recordings"));
        addBaseDir(addedPaths, new File("/sdcard/Android/data/com.whatsapp/files/Recordings"));
        addBaseDir(addedPaths, new File("/sdcard/Android/data/com.whatsapp.w4b/files/Recordings"));
        addBaseDir(addedPaths, new File(Environment.getExternalStorageDirectory(), "Music/WaEnhancer/Recordings"));
    }

    private void addBaseDir(@NonNull Set<String> addedPaths, @NonNull File dir) {
        String normalizedPath = normalizePath(dir);
        if (addedPaths.add(normalizedPath)) {
            baseDirs.add(dir);
        }
    }

    @NonNull
    private String normalizePath(@NonNull File dir) {
        try {
            return dir.getCanonicalPath();
        } catch (IOException ignored) {
            return dir.getAbsolutePath();
        }
    }

    private void loadRecordings() {
        if (binding == null) {
            return;
        }

        binding.swipeRefresh.setRefreshing(true);
        executorService.execute(() -> {
            List<Recording> recordings = new ArrayList<>();
            try {
                for (File baseDir : baseDirs) {
                    if (baseDir.exists() && baseDir.isDirectory()) {
                        traverseDirectory(baseDir, recordings);
                    }
                }

                applySort(recordings);

                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding == null) {
                            return;
                        }
                        allRecordings.clear();
                        allRecordings.addAll(recordings);
                        adapter.setRecordings(allRecordings);
                        binding.emptyView.setVisibility(allRecordings.isEmpty() ? View.VISIBLE : View.GONE);
                        binding.recyclerView.setVisibility(allRecordings.isEmpty() ? View.GONE : View.VISIBLE);
                        binding.swipeRefresh.setRefreshing(false);
                    });
                }
            } catch (Exception ignored) {
                if (getActivity() != null) {
                    requireActivity().runOnUiThread(() -> {
                        if (binding != null) {
                            binding.swipeRefresh.setRefreshing(false);
                        }
                    });
                }
            }
        });
    }

    private void traverseDirectory(@NonNull File dir, List<Recording> recordings) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                traverseDirectory(file, recordings);
                continue;
            }

            String name = file.getName().toLowerCase();
            if (name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".m4a")) {
                recordings.add(new Recording(file));
            }
        }
    }

    private void applySort(List<Recording> recordings) {
        switch (currentSortType) {
            case 2 -> recordings.sort(Comparator.comparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER));
            case 3 -> recordings.sort((left, right) -> Long.compare(right.getDuration(), left.getDuration()));
            case 4 -> recordings.sort(Comparator.comparing(Recording::getContactName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing((left, right) -> Long.compare(right.getDate(), left.getDate())));
            default -> recordings.sort((left, right) -> Long.compare(right.getDate(), left.getDate()));
        }
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_recordings_action, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.action_select_all) {
                adapter.selectAll();
                return true;
            } else if (itemId == R.id.action_share) {
                shareSelectedRecordings();
                return true;
            } else if (itemId == R.id.action_delete) {
                deleteSelectedRecordings();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            adapter.clearSelection();
            actionMode = null;
        }
    };

    @Override
    public void onPlay(Recording recording) {
        new AudioPlayerDialog(requireContext(), recording.getFile()).show();
    }

    @Override
    public void onShare(Recording recording) {
        shareRecording(recording.getFile());
    }

    @Override
    public void onDelete(Recording recording) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(recording.getFile().getName())
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    if (recording.getFile().delete()) {
                        loadRecordings();
                    } else {
                        Toast.makeText(requireContext(), R.string.recording_delete_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onLongPress(Recording recording, int position) {
        adapter.setSelectionMode(true);
        adapter.toggleSelection(position);
    }

    private void shareRecording(@NonNull File file) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("audio/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recording)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), getString(R.string.recording_share_failed, e.getMessage()), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) {
            return;
        }
        if (selected.size() == 1) {
            shareRecording(selected.get(0).getFile());
            adapter.clearSelection();
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (Recording recording : selected) {
            try {
                uris.add(FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        recording.getFile()
                ));
            } catch (Exception ignored) {
            }
        }

        if (!uris.isEmpty()) {
            Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.setType("audio/*");
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, getString(R.string.share_recordings)));
        }
        adapter.clearSelection();
    }

    private void deleteSelectedRecordings() {
        List<Recording> selected = adapter.getSelectedRecordings();
        if (selected.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_confirmation)
                .setMessage(getString(R.string.delete_multiple_confirmation, selected.size()))
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    int deleted = 0;
                    for (Recording recording : selected) {
                        if (recording.getFile().delete()) {
                            deleted++;
                        }
                    }
                    Toast.makeText(requireContext(), getString(R.string.recording_deleted_count, deleted), Toast.LENGTH_SHORT).show();
                    adapter.clearSelection();
                    loadRecordings();
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
