package com.waenhancer.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.waenhancer.R;
import com.waenhancer.adapter.DeletedMessagesAdapter;
import com.waenhancer.xposed.core.db.DelMessageStore;
import com.waenhancer.xposed.core.db.DeletedMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeletedMessagesFragment extends Fragment implements DeletedMessagesAdapter.OnItemClickListener {

    private RecyclerView recyclerView;
    private View emptyView;
    private com.facebook.shimmer.ShimmerFrameLayout shimmerViewContainer;
    private DeletedMessagesAdapter adapter;
    private DelMessageStore delMessageStore;

    private boolean isGroup;
    private final android.os.Handler uiHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable showShimmerRunnable;

    public static DeletedMessagesFragment newInstance(boolean isGroup) {
        DeletedMessagesFragment fragment = new DeletedMessagesFragment();
        Bundle args = new Bundle();
        args.putBoolean("is_group", isGroup);
        fragment.setArguments(args);
        return fragment;
    }

    private int currentFilter = R.id.filter_all;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isGroup = getArguments().getBoolean("is_group");
        }
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_deleted_messages, container, false);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull android.view.Menu menu, @NonNull android.view.MenuInflater inflater) {
        inflater.inflate(R.menu.menu_deleted_messages, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.filter_all || item.getItemId() == R.id.filter_whatsapp
                || item.getItemId() == R.id.filter_whatsapp_business) {
            currentFilter = item.getItemId();
            item.setChecked(true);
            loadMessages();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView = view.findViewById(R.id.empty_view);
        shimmerViewContainer = view.findViewById(R.id.shimmer_view_container);

        delMessageStore = DelMessageStore.getInstance(requireContext());
        adapter = new DeletedMessagesAdapter(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // Check for updates to names if permission is already granted, but don't ask
        if (requireContext().checkSelfPermission(
                android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            adapter.notifyDataSetChanged();
        }

        loadMessages();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (requireContext().checkSelfPermission(
                android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            if (adapter != null)
                adapter.notifyDataSetChanged();
        }
        loadMessages();
    }

    private void loadMessages() {
        if (showShimmerRunnable != null) {
            uiHandler.removeCallbacks(showShimmerRunnable);
        }

        showShimmerRunnable = () -> {
            if (shimmerViewContainer != null) {
                shimmerViewContainer.setVisibility(View.VISIBLE);
                shimmerViewContainer.startShimmer();
            }
            if (recyclerView != null) {
                recyclerView.setVisibility(View.GONE);
            }
            if (emptyView != null) {
                emptyView.setVisibility(View.GONE);
            }
        };
        uiHandler.postDelayed(showShimmerRunnable, 150);

        new Thread(() -> {
            List<DeletedMessage> allMessages = delMessageStore.getDeletedMessages(isGroup);
            Map<String, DeletedMessage> latestMessagesMap = new HashMap<>();

            for (DeletedMessage msg : allMessages) {
                // Apps Filter Logic
                boolean matchesFilter = true;
                if (currentFilter == R.id.filter_whatsapp) {
                    matchesFilter = "com.whatsapp".equals(msg.getPackageName());
                } else if (currentFilter == R.id.filter_whatsapp_business) {
                    matchesFilter = "com.whatsapp.w4b".equals(msg.getPackageName());
                }

                if (!matchesFilter)
                    continue;

                if (!latestMessagesMap.containsKey(msg.getChatJid())) {
                    latestMessagesMap.put(msg.getChatJid(), msg);
                } else {
                    if (msg.getTimestamp() > latestMessagesMap.get(msg.getChatJid()).getTimestamp()) {
                        latestMessagesMap.put(msg.getChatJid(), msg);
                    }
                }
            }

            List<DeletedMessage> uniqueChats = new ArrayList<>(latestMessagesMap.values());
            uniqueChats.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (showShimmerRunnable != null) {
                    uiHandler.removeCallbacks(showShimmerRunnable);
                }

                if (shimmerViewContainer != null) {
                    shimmerViewContainer.stopShimmer();
                    shimmerViewContainer.setVisibility(View.GONE);
                }

                if (uniqueChats.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                    adapter.setMessages(uniqueChats);
                }
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
        if (showShimmerRunnable != null) {
            uiHandler.removeCallbacks(showShimmerRunnable);
        }
        super.onDestroyView();
    }

    private androidx.appcompat.view.ActionMode actionMode;
    private final androidx.appcompat.view.ActionMode.Callback actionModeCallback = new androidx.appcompat.view.ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(androidx.appcompat.view.ActionMode mode, android.view.Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_context_delete, menu); // Need to create this menu
            return true;
        }

        @Override
        public boolean onPrepareActionMode(androidx.appcompat.view.ActionMode mode, android.view.Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(androidx.appcompat.view.ActionMode mode, android.view.MenuItem item) {
            if (item.getItemId() == R.id.action_delete) {
                deleteSelectedChats();
                mode.finish();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(androidx.appcompat.view.ActionMode mode) {
            adapter.clearSelection();
            actionMode = null;
        }
    };

    private void deleteSelectedChats() {
        List<String> selected = adapter.getSelectedItems();
        if (selected.isEmpty())
            return;

        com.waenhancer.ui.helpers.BottomSheetHelper.showConfirmation(
                requireContext(),
                "Delete Chats?",
                "Are you sure you want to delete " + selected.size() + " chat(s)? This cannot be undone.",
                "Delete",
                true,
                () -> {
                    new Thread(() -> {
                        for (String jid : selected) {
                            delMessageStore.deleteMessagesByChat(jid);
                        }
                        requireActivity().runOnUiThread(() -> {
                            loadMessages();
                            android.widget.Toast
                                    .makeText(requireContext(), "Chats deleted", android.widget.Toast.LENGTH_SHORT)
                                    .show();
                        });
                    }).start();
                });
    }

    @Override
    public void onItemClick(DeletedMessage message) {
        if (actionMode != null) {
            toggleSelection(message.getChatJid());
        } else {
            android.content.Intent intent = new android.content.Intent(requireContext(),
                    com.waenhancer.activities.MessageListActivity.class);
            intent.putExtra("chat_jid", message.getChatJid());
            startActivity(intent);
        }
    }

    @Override
    public boolean onItemLongClick(DeletedMessage message) {
        if (actionMode == null) {
            actionMode = ((androidx.appcompat.app.AppCompatActivity) requireActivity())
                    .startSupportActionMode(actionModeCallback);
        }
        toggleSelection(message.getChatJid());
        return true;
    }

    private void toggleSelection(String chatJid) {
        adapter.toggleSelection(chatJid);
        int count = adapter.getSelectedCount();
        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(count + " selected");
        }
    }

    @Override
    public void onRestoreClick(DeletedMessage message) {
        // Not used
    }
}
