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
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private ArrayList<ChatMessage> messages;
    private Markwon markwon;
    private OnPlacesButtonClickListener listener;
    private Map<String, Spanned> markdownCache = new HashMap<>();

    public interface OnPlacesButtonClickListener {
        void onPlacesButtonClick(String jsonMetadata);
    }

    public ChatAdapter(Context context, ArrayList<ChatMessage> messages, OnPlacesButtonClickListener listener) {
        this.messages = messages;
        this.markwon = Markwon.builder(context)
                .usePlugin(TablePlugin.create(context))
                .usePlugin(StrikethroughPlugin.create())
                .build();
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
             renderAndSetText(((UserViewHolder) holder).tvMessage, msg.getMessage());
        } else if (holder instanceof AIViewHolder) {
            AIViewHolder aiHolder = (AIViewHolder) holder;
            
            String fullMessage = msg.getMessage();
            if (fullMessage == null) fullMessage = "";

            // --- SILENT THINKING LOGIC ---
            String answerContent = "";
            boolean isThinking = false;

            // Xử lý Placeholder "<think>" từ MainActivity
            if (fullMessage.equals("<think>")) {
                isThinking = true;
                answerContent = "";
            } else {
                int tO = fullMessage.indexOf("<think>");
                int tC = fullMessage.indexOf("</think>");
                int sep = fullMessage.indexOf("|||");

                if (tO != -1) {
                    if (tC != -1) {
                        isThinking = false;
                        int endOfThink = tC + 8;
                        if (sep != -1 && sep > endOfThink) {
                            answerContent = fullMessage.substring(endOfThink, sep).trim();
                        } else {
                            answerContent = fullMessage.substring(endOfThink).trim();
                        }
                    } else {
                        // Đang suy nghĩ
                        if (sep != -1) {
                            isThinking = false;
                            answerContent = ""; 
                        } else {
                            isThinking = true;
                            answerContent = ""; 
                        }
                    }
                } else {
                    isThinking = false;
                    if (sep != -1) {
                        answerContent = fullMessage.substring(0, sep).trim();
                    } else {
                        answerContent = fullMessage;
                    }
                }
            }

            // Hiển thị thanh trạng thái "Đang suy nghĩ"
            aiHolder.layoutThinkingStatus.setVisibility(isThinking ? View.VISIBLE : View.GONE);

            // Hiển thị câu trả lời chính
            if (!answerContent.isEmpty()) {
                aiHolder.tvMessage.setVisibility(View.VISIBLE);
                renderAndSetText(aiHolder.tvMessage, answerContent);
            } else {
                // Fallback: Xong rồi mà không có text (chỉ có JSON)
                if (!isThinking && msg.metadata != null && !msg.metadata.isEmpty() && !msg.metadata.equals("[]")) {
                    aiHolder.tvMessage.setVisibility(View.VISIBLE);
                    renderAndSetText(aiHolder.tvMessage, "Đã tìm thấy địa điểm:");
                } else {
                    aiHolder.tvMessage.setVisibility(View.GONE);
                }
            }
            
            // Xử lý nút địa điểm
            if (msg.metadata != null && !msg.metadata.isEmpty() && !msg.metadata.equals("[]")) {
                aiHolder.btnViewPlaces.setVisibility(View.VISIBLE);
                aiHolder.btnViewPlaces.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onPlacesButtonClick(msg.metadata);
                    }
                });
            } else {
                aiHolder.btnViewPlaces.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull java.util.List<Object> payloads) {
        if (!payloads.isEmpty()) {
            for (Object payload : payloads) {
                if ("STREAMING_UPDATE".equals(payload)) {
                    if (holder instanceof AIViewHolder) {
                        onBindViewHolder(holder, position); 
                    }
                    return; 
                }
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void renderAndSetText(TextView textView, String text) {
        if (text == null || text.trim().isEmpty()) return;
        String cacheKey = String.valueOf(text.hashCode());
        Spanned cached = markdownCache.get(cacheKey);
        if (cached == null) {
            cached = markwon.toMarkdown(text);
            markdownCache.put(cacheKey, cached);
        }
        markwon.setParsedMarkdown(textView, cached);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    public void updateStreamingMessage(int position) {
        if (position >= 0 && position < messages.size()) {
            notifyItemChanged(position, "STREAMING_UPDATE");
        }
    }
    
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
        View layoutThinkingStatus;

        AIViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnViewPlaces = itemView.findViewById(R.id.btnViewPlaces);
            layoutThinkingStatus = itemView.findViewById(R.id.layoutThinkingStatus);
        }
    }
}