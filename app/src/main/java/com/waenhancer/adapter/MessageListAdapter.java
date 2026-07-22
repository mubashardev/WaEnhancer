package com.waenhancer.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.waenhancer.R;
import com.waenhancer.xposed.core.db.DeletedMessage;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Color;
import android.util.TypedValue;
import android.widget.Toast;
import com.waenhancer.utils.ContactHelper;
import java.util.HashSet;
import java.util.Set;

public class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.ViewHolder> {

    private Set<String> selectedItems = new HashSet<>();
    private List<DeletedMessage> messages = new ArrayList<>();
    private final OnRestoreClickListener listener;

    public interface OnRestoreClickListener {
        void onRestoreClick(DeletedMessage message);

        boolean onItemLongClick(DeletedMessage message);

        void onItemClick(DeletedMessage message);
    }

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    public MessageListAdapter(OnRestoreClickListener listener) {
        this.listener = listener;
    }

    public void setMessages(List<DeletedMessage> messages) {
        this.messages = messages;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        DeletedMessage message = messages.get(position);
        return message.isFromMe() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = (viewType == VIEW_TYPE_SENT) ? R.layout.item_message_sent : R.layout.item_message_received;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DeletedMessage message = messages.get(position);
        Context context = holder.itemView.getContext();

        if (holder.senderName != null) {
            boolean showName = !message.isFromMe() && message.getChatJid().contains("@g.us");

            if (showName) {
                String contactName = ContactHelper.getContactName(context,
                        message.getSenderJid());

                if (contactName != null) {
                    holder.senderName.setText(contactName);
                    holder.senderName.setVisibility(View.VISIBLE);
                } else {
                    String senderJid = message.getSenderJid();
                    if (senderJid != null) {
                        senderJid = senderJid.replace("@s.whatsapp.net", "").replace("@g.us", "");
                        if (senderJid.contains("@"))
                            senderJid = senderJid.split("@")[0];
                        holder.senderName.setText(senderJid);
                        holder.senderName.setVisibility(View.VISIBLE);
                    } else {
                        holder.senderName.setVisibility(View.GONE);
                    }
                }
            } else {
                holder.senderName.setVisibility(View.GONE);
            }
        }

        String timeText = "Deleted:\t" + Utils.getDateTimeFromMillis(message.getTimestamp());
        if (message.getOriginalTimestamp() > 0) {
            timeText = "Original:\t" + Utils.getDateTimeFromMillis(message.getOriginalTimestamp()) + "\n" + timeText;
        }
        holder.timestamp.setText(timeText);

        String mediaPath = message.getMediaPath();
        int mediaType = message.getMediaType();
        File mediaFile = null;
        if (mediaPath != null) {
            if (mediaPath.startsWith("/")) {
                mediaFile = new File(mediaPath);
            } else {
                mediaFile = new File(context.getFilesDir(), mediaPath);
            }
        }
        boolean hasMedia = mediaFile != null && mediaFile.exists() && mediaType > 0;

        if (hasMedia) {
            holder.mediaCard.setVisibility(View.VISIBLE);
            bindMediaPreview(holder, mediaFile, mediaType, context);

            final File finalMediaFile = mediaFile;
            holder.mediaCard.setOnClickListener(v -> openMedia(context, finalMediaFile, mediaType));
        } else {
            holder.mediaCard.setVisibility(View.GONE);
            holder.mediaPlayIcon.setVisibility(View.GONE);
        }

        String text = message.getTextContent();
        String mediaCaption = message.getMediaCaption();

        if (hasMedia && mediaCaption != null && !mediaCaption.isEmpty()) {
            holder.messageContent.setText(mediaCaption);
            holder.messageContent.setVisibility(View.VISIBLE);
        } else if (hasMedia && (text == null || text.isEmpty())) {
            String typeLabel = getMediaTypeLabel(mediaType);
            holder.messageContent.setText(typeLabel);
            holder.messageContent.setVisibility(View.VISIBLE);
        } else if (text != null && !text.isEmpty()) {
            holder.messageContent.setText(text);
            holder.messageContent.setVisibility(View.VISIBLE);
        } else {
            String type = "Message";
            if (mediaType != -1 && mediaType != 0) {
                type = getMediaTypeLabel(mediaType);
                if (!hasMedia) {
                    type += " (file unavailable)";
                }
            }
            if (mediaCaption != null && !mediaCaption.isEmpty()) {
                type += "\n" + mediaCaption;
            }
            holder.messageContent.setText(type);
            holder.messageContent.setVisibility(View.VISIBLE);
        }

        holder.btnRestore.setOnClickListener(v -> listener.onRestoreClick(message));

        if (selectedItems.contains(message.getKeyId())) {
            TypedValue typedValue = new TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.colorControlHighlight, typedValue, true);
            holder.itemView.setBackgroundColor(typedValue.data);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
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

    private void bindMediaPreview(ViewHolder holder, File mediaFile, int mediaType, Context context) {
        switch (mediaType) {
            case 1:
            case 42:
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(mediaFile.getAbsolutePath());
                    if (bmp != null) {
                        holder.mediaPreview.setImageBitmap(bmp);
                    } else {
                        holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } catch (Throwable t) {
                    holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                holder.mediaPlayIcon.setVisibility(View.GONE);
                break;

            case 3:
            case 43:
            case 13:
                try {
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(mediaFile.getAbsolutePath());
                    Bitmap frame = retriever.getFrameAtTime(0);
                    retriever.release();
                    if (frame != null) {
                        holder.mediaPreview.setImageBitmap(frame);
                    } else {
                        holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } catch (Throwable t) {
                    holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                holder.mediaPlayIcon.setVisibility(View.VISIBLE);
                break;

            case 2:
            case 82:
                holder.mediaPreview.setImageResource(android.R.drawable.ic_btn_speak_now);
                holder.mediaPlayIcon.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = holder.mediaPreview.getLayoutParams();
                lp.height = dpToPx(context, 60);
                lp.width = dpToPx(context, 220);
                holder.mediaPreview.setLayoutParams(lp);
                holder.mediaPreview.setScaleType(ImageView.ScaleType.CENTER);
                break;

            case 20:
                try {
                    Bitmap sticker = BitmapFactory.decodeFile(mediaFile.getAbsolutePath());
                    if (sticker != null) {
                        holder.mediaPreview.setImageBitmap(sticker);
                        holder.mediaPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        ViewGroup.LayoutParams slp = holder.mediaPreview.getLayoutParams();
                        slp.height = dpToPx(context, 150);
                        slp.width = dpToPx(context, 150);
                        holder.mediaPreview.setLayoutParams(slp);
                    } else {
                        holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                    }
                } catch (Throwable t) {
                    holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_gallery);
                }
                holder.mediaPlayIcon.setVisibility(View.GONE);
                break;

            default:
                holder.mediaPreview.setImageResource(android.R.drawable.ic_menu_save);
                holder.mediaPlayIcon.setVisibility(View.GONE);
                ViewGroup.LayoutParams dlp = holder.mediaPreview.getLayoutParams();
                dlp.height = dpToPx(context, 80);
                dlp.width = dpToPx(context, 220);
                holder.mediaPreview.setLayoutParams(dlp);
                holder.mediaPreview.setScaleType(ImageView.ScaleType.CENTER);
                break;
        }
    }

    private void openMedia(Context context, File mediaFile, int mediaType) {
        try {
            String mimeType = getMimeType(mediaFile, mediaType);
            Uri uri;
            try {
                String authority = context.getPackageName() + ".fileprovider";
                uri = FileProvider.getUriForFile(context, authority, mediaFile);
            } catch (IllegalArgumentException e) {
                uri = Uri.fromFile(mediaFile);
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context, "Cannot open media file", Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(File file, int mediaType) {
        String ext = "";
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            ext = name.substring(dot + 1).toLowerCase();
        }

        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime != null) return mime;

        switch (mediaType) {
            case 1:
            case 42:
                return "image/*";
            case 3:
            case 43:
            case 13:
                return "video/*";
            case 2:
            case 82:
                return "audio/*";
            case 9:
                return "application/*";
            case 20:
                return "image/webp";
            default:
                return "*/*";
        }
    }

    private String getMediaTypeLabel(int mediaType) {
        switch (mediaType) {
            case 1:
                return "📷 Photo";
            case 42:
                return "📷 View Once Photo";
            case 2:
                return "🔊 Audio";
            case 82:
                return "🔊 View Once Voice Note";
            case 3:
                return "🎥 Video";
            case 43:
                return "🎥 View Once Video";
            case 4:
                return "👤 Contact";
            case 5:
                return "📍 Location";
            case 9:
                return "📄 Document";
            case 13:
                return "👾 GIF";
            case 20:
                return "💟 Sticker";
            default:
                return "📁 Media (" + mediaType + ")";
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    public void toggleSelection(String keyId) {
        if (selectedItems.contains(keyId)) {
            selectedItems.remove(keyId);
        } else {
            selectedItems.add(keyId);
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
        TextView senderName;
        TextView timestamp;
        TextView messageContent;
        View btnRestore;
        MaterialCardView mediaCard;
        ImageView mediaPreview;
        ImageView mediaPlayIcon;

        ViewHolder(View itemView) {
            super(itemView);
            senderName = itemView.findViewById(R.id.sender_name);
            timestamp = itemView.findViewById(R.id.timestamp);
            messageContent = itemView.findViewById(R.id.message_content);
            btnRestore = itemView.findViewById(R.id.btn_restore);
            mediaCard = itemView.findViewById(R.id.media_card);
            mediaPreview = itemView.findViewById(R.id.media_preview);
            mediaPlayIcon = itemView.findViewById(R.id.media_play_icon);
        }
    }
}