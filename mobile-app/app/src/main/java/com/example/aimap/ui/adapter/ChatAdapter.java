package com.example.aimap.ui.adapter;

import android.content.Context;
import android.text.Spanned;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.data.ChatMessage;
import com.example.aimap.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.noties.markwon.Markwon;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private ArrayList<ChatMessage> messages;
    private Markwon markwon;
    private OnPlacesButtonClickListener listener;
    private Map<String, Spanned> markdownCache = new HashMap<>(); // Cache markdown

    public interface OnPlacesButtonClickListener {
        void onPlacesButtonClick(String jsonMetadata);
    }

    public ChatAdapter(Context context, ArrayList<ChatMessage> messages, OnPlacesButtonClickListener listener) {
        this.messages = messages;
        this.markwon = Markwon.create(context);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == ChatMessage.TYPE_USER) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_ai, parent, false);
            return new AIViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = messages.get(position);
        if (holder instanceof UserViewHolder) {
             renderAndSetText(((UserViewHolder) holder).tvMessage, msg);
        } else if (holder instanceof AIViewHolder) {
            AIViewHolder aiHolder = (AIViewHolder) holder;
            renderAndSetText(aiHolder.tvMessage, msg);
            bindButtonState(aiHolder, msg); // Cập nhật trạng thái nút
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if ("UPDATE_BUTTON".equals(payload)) {
                    if (holder instanceof AIViewHolder) {
                        bindButtonState((AIViewHolder) holder, messages.get(position));
                    }
                    return; // Đã xử lý xong partial update cho nút
                } else if ("STREAMING_UPDATE".equals(payload)) {
                    // Chỉ update TEXT, không đụng vào Button hay các view khác
                    if (holder instanceof AIViewHolder) {
                        renderAndSetText(((AIViewHolder) holder).tvMessage, messages.get(position));
                    }
                    return; // Đã xử lý xong partial update cho streaming text
                }
            }
        }
        // Nếu không có payload hoặc payload lạ, gọi bind đầy đủ
        super.onBindViewHolder(holder, position, payloads);
    }

    private void bindButtonState(AIViewHolder aiHolder, ChatMessage msg) {
        if (msg.metadata != null && !msg.metadata.isEmpty()) {
            aiHolder.btnViewPlaces.setVisibility(View.VISIBLE);
            aiHolder.btnViewPlaces.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPlacesButtonClick(msg.metadata);
                }
            });
        } else {
            aiHolder.btnViewPlaces.setVisibility(View.GONE);
            aiHolder.btnViewPlaces.setOnClickListener(null);
        }
    }
    
    private void renderAndSetText(TextView textView, ChatMessage msg) {
        Spanned cached = markdownCache.get(msg.message_id);
        if (cached == null) {
            cached = markwon.toMarkdown(msg.getMessage());
            markdownCache.put(msg.message_id, cached);
        }
        markwon.setParsedMarkdown(textView, cached);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(ChatMessage message){
        messages.add(message);
        // Xóa cache để đảm bảo nó được render mới khi add
        // Không, cái này chỉ nên xóa khi message cũ bị thay đổi.
        // Khi add mới, nó chưa có trong cache nên không cần xóa.
        notifyItemInserted(messages.size() - 1);
    }
    
    // Phương thức mới để cập nhật message đang streaming
    public void updateStreamingMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            // Xóa cache của message này vì nội dung text đã thay đổi
            markdownCache.remove(messages.get(position).message_id);
            // Gọi notify với payload để chỉ cập nhật text
            notifyItemChanged(position, "STREAMING_UPDATE");
        }
    }
    
    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        markdownCache.clear();
        notifyItemRangeRemoved(0, size);
    }
    
    // ... ViewHolder classes
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }

    static class AIViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;
        MaterialButton btnViewPlaces;

        AIViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnViewPlaces = itemView.findViewById(R.id.btnViewPlaces);
        }
    }
}