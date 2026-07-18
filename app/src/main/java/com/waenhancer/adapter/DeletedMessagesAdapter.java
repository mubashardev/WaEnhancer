package com.waenhancer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.waenhancer.R;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.db.DeletedMessage;
import com.waenhancer.xposed.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import com.waenhancer.utils.ContactHelper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class DeletedMessagesAdapter extends RecyclerView.Adapter<DeletedMessagesAdapter.ViewHolder> {

    private List<DeletedMessage> messages = new ArrayList<>();
    private Set<String> selectedItems = new HashSet<>();
    private final Map<String, Drawable> iconCache = new HashMap<>();
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DeletedMessage message);

        boolean onItemLongClick(DeletedMessage message);

        void onRestoreClick(DeletedMessage message);
    }

    public DeletedMessagesAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<DeletedMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);

        // Contact Name
        Context context = holder.itemView.getContext();
        String displayJid = message.getChatJid();

        String contactName = message.getContactName();
        if (contactName != null) {
            String cleanJid = displayJid != null ? displayJid.replace("@s.whatsapp.net", "").replace("@g.us", "").split("@")[0] : "";
            String cleanContact = contactName.replace("@s.whatsapp.net", "").replace("@g.us", "").split("@")[0];
            if (cleanContact.equals(cleanJid) || contactName.equalsIgnoreCase("WhatsApp") || contactName.equalsIgnoreCase("WhatsApp Business")) {
                contactName = null;
            }
        }

        if ((contactName == null || contactName.isEmpty()) && displayJid != null) {
            contactName = ContactHelper.getContactName(context, displayJid);
        }

        if (contactName != null
                && (contactName.equalsIgnoreCase("WhatsApp") || contactName.equalsIgnoreCase("WhatsApp Business"))) {
            contactName = null;
        }
        String displayText;
        if (contactName != null) {
            displayText = contactName;
        } else {
            // Fallback to formatted JID if contact name is missing
            displayText = displayJid;
            if (displayText != null) {
                displayText = displayText.replace("@s.whatsapp.net", "").replace("@g.us", "");
                if (displayText.contains("@"))
                    displayText = displayText.split("@")[0];
            } else {
                displayText = "Unknown";
            }
        }
        holder.contactName.setText(displayText);

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm a",
                Locale.getDefault());
        holder.timestamp.setText(sdf.format(new Date(message.getTimestamp())));

        // Message Preview Logic... (unchanged)
        String text = message.getTextContent();
        String senderPrefix = "";
        if (message.isFromMe()) {
            senderPrefix = "You: ";
        }

        // Sanitize text for media messages (Fix for weird strings/URLs)
        if (message.getMediaType() > 0 && text != null) {
            if (text.startsWith("http") || (text.length() > 20 && !text.contains(" "))) {
                text = null;
            }
        }

        if (text == null || text.isEmpty()) {
            int type = message.getMediaType();
            if (type != -1 && type != 0) {
                switch (type) {
                    case 1:
                        text = "📷 Photo";
                        break;
                    case 2:
                        text = "🔊 Audio";
                        break;
                    case 3:
                        text = "🎥 Video";
                        break;
                    case 4:
                        text = "👤 Contact";
                        break;
                    case 5:
                        text = "📍 Location";
                        break;
                    case 9:
                        text = "📄 Document";
                        break;
                    case 13:
                        text = "👾 GIF";
                        break;
                    case 20:
                        text = "💟 Sticker";
                        break;
                    case 42:
                        text = "🔄 Status Reply";
                        break;
                    default:
                        text = "📁 Media";
                        break;
                }
            } else {
                text = "🚫 Message deleted";
            }
        }
        holder.lastMessage.setText(senderPrefix + text);

        // Avatar (Placeholder)
        // Holder.avatar.setImageDrawable(...)

        // Avatar (Now App Icon) & App Badge Logic
        String pkg = message.getPackageName();
        if (pkg != null) {
            // Hide the small badge
            holder.appBadge.setVisibility(View.GONE);

            // Clear any tint on the avatar (xml has tint)
            holder.avatar.setImageTintList(null);

            // Try to load from cache first
            if (iconCache.containsKey(pkg)) {
                holder.avatar.setImageDrawable(iconCache.get(pkg));
            } else {
                try {
                    PackageManager pm = holder.itemView.getContext().getPackageManager();
                    Drawable icon = pm.getApplicationIcon(pkg);
                    if (icon != null) {
                        iconCache.put(pkg, icon);
                        holder.avatar.setImageDrawable(icon);
                    } else {
                        // Fallback
                        holder.avatar.setImageResource(R.drawable.ic_person);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // App not installed or invalid package name
                    holder.avatar.setImageResource(R.drawable.ic_person);
                }
            }
        } else {
            // Fallback if no package name (shouldn't happen for new msgs)
            holder.avatar.setImageResource(R.drawable.ic_person);
            holder.appBadge.setVisibility(View.GONE);
        }

        // Selection Logic
        if (selectedItems.contains(message.getChatJid())) {
            TypedValue outValue = new TypedValue();
            holder.itemView.getContext().getTheme()
                    .resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, outValue, true);
            holder.itemView.setBackgroundColor(outValue.data);
        } else {
            TypedValue outValue = new TypedValue();
            holder.itemView.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue,
                    true);
            holder.itemView.setBackgroundResource(outValue.resourceId);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onItemClick(message);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null)
                return listener.onItemLongClick(message);
            return false;
        });
    }

    public void toggleSelection(String chatJid) {
        if (selectedItems.contains(chatJid)) {
            selectedItems.remove(chatJid);
        } else {
            selectedItems.add(chatJid);
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();
    }

    public int getSelectedCount() {
        return selectedItems.size();
    }

    public List<String> getSelectedItems() {
        return new ArrayList<>(selectedItems);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        ImageView appBadge;
        TextView contactName;
        TextView timestamp; // Date
        TextView lastMessage;

        ViewHolder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.avatar);
            appBadge = itemView.findViewById(R.id.app_badge);
            contactName = itemView.findViewById(R.id.contact_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            lastMessage = itemView.findViewById(R.id.last_message);
        }
    }
}