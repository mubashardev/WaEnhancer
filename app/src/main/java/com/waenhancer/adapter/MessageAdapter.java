package com.waenhancer.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.db.MessageHistory;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.util.List;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StrikethroughSpan;
import com.waenhancer.xposed.core.FeatureLoader;
import java.util.ArrayList;

public class MessageAdapter extends ArrayAdapter<MessageHistory.MessageItem> {
    private final Context context;
    private final List<MessageHistory.MessageItem> items;
    private boolean showDiff = false;

    public MessageAdapter(Context context, List<MessageHistory.MessageItem> items) {
        super(context, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        this.context = context;
        this.items = items;
    }

    public void setShowDiff(boolean showDiff) {
        this.showDiff = showDiff;
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public MessageHistory.MessageItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View view1 = super.getView(position, convertView, parent);
        TextView textView0 = view1.findViewById(android.R.id.text1);
        textView0.setTextSize(14.0f);
        textView0.setTextColor(DesignUtils.getPrimaryTextColor());

        var messageItem = this.items.get(position);
        if (showDiff && position > 0 && !items.isEmpty()) {
            String original = items.get(0).message;
            String current = messageItem.message;
            textView0.setText(DiffHelper.getSpannableDiff(original, current));
        } else {
            textView0.setText(messageItem.message);
        }

        TextView textView1 = view1.findViewById(android.R.id.text2);
        textView1.setTextSize(12.0f);
        textView1.setAlpha(0.75f);
        textView1.setTypeface(null, Typeface.ITALIC);
        textView1.setTextColor(DesignUtils.getPrimaryTextColor());
        var timestamp = messageItem.timestamp;
        textView1.setText((timestamp == 0L ? FeatureLoader.getModuleString(Utils.getApplication(), R.string.message_original, "Original Message") : "✏️ " + Utils.getDateTimeFromMillis(timestamp)));
        return view1;
    }

    public static class DiffHelper {
        public enum Type {
            UNCHANGED, INSERTED, DELETED
        }

        public static class Chunk {
            public final Type type;
            public final String text;

            public Chunk(Type type, String text) {
                this.type = type;
                this.text = text;
            }
        }

        public static List<Chunk> diff(String original, String revision) {
            if (original == null) original = "";
            if (revision == null) revision = "";

            char[] A = original.toCharArray();
            char[] B = revision.toCharArray();
            int m = A.length;
            int n = B.length;

            int[][] L = new int[m + 1][n + 1];
            for (int i = 0; i <= m; i++) {
                for (int j = 0; j <= n; j++) {
                    if (i == 0 || j == 0) {
                        L[i][j] = 0;
                    } else if (A[i - 1] == B[j - 1]) {
                        L[i][j] = L[i - 1][j - 1] + 1;
                    } else {
                        L[i][j] = Math.max(L[i - 1][j], L[i][j - 1]);
                    }
                }
            }

            int i = m, j = n;
            List<Chunk> chunks = new ArrayList<>();
            StringBuilder currentText = new StringBuilder();
            Type currentType = null;

            while (i > 0 || j > 0) {
                Type nextType;
                char c;
                if (i > 0 && j > 0 && A[i - 1] == B[j - 1]) {
                    nextType = Type.UNCHANGED;
                    c = A[i - 1];
                    i--;
                    j--;
                } else if (j > 0 && (i == 0 || L[i][j - 1] >= L[i - 1][j])) {
                    nextType = Type.INSERTED;
                    c = B[j - 1];
                    j--;
                } else {
                    nextType = Type.DELETED;
                    c = A[i - 1];
                    i--;
                }

                if (currentType == null) {
                    currentType = nextType;
                    currentText.append(c);
                } else if (currentType == nextType) {
                    currentText.append(c);
                } else {
                    chunks.add(0, new Chunk(currentType, currentText.reverse().toString()));
                    currentText.setLength(0);
                    currentType = nextType;
                    currentText.append(c);
                }
            }

            if (currentType != null && currentText.length() > 0) {
                chunks.add(0, new Chunk(currentType, currentText.reverse().toString()));
            }

            return chunks;
        }

        public static SpannableStringBuilder getSpannableDiff(String original, String revision) {
            List<Chunk> chunks = diff(original, revision);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            for (Chunk chunk : chunks) {
                int start = builder.length();
                builder.append(chunk.text);
                int end = builder.length();
                if (chunk.type == Type.INSERTED) {
                    builder.setSpan(new BackgroundColorSpan(0x334CAF50), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (chunk.type == Type.DELETED) {
                    builder.setSpan(new BackgroundColorSpan(0x33F44336), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            return builder;
        }
    }
}