package com.example.aimap.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.data.Session;
import com.example.aimap.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {
    private List<Session> sessions;
    private OnSessionClickListener listener;
    private OnSessionActionListener actionListener;

    public interface OnSessionClickListener {
        void onSessionClick(Session session);
    }

    public interface OnSessionActionListener {
        void onDeleteSession(Session session);
        void onRenameSession(Session session);
        void onDuplicateSession(Session session);
    }

    public SessionAdapter(OnSessionClickListener listener, OnSessionActionListener actionListener) {
        this.sessions = new ArrayList<>();
        this.listener = listener;
        this.actionListener = actionListener;
    }

    public void updateSessions(List<Session> newSessions) {
        this.sessions.clear();
        this.sessions.addAll(newSessions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.session_item, parent, false);
        return new SessionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);
        holder.bind(session);
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSessionTitle, tvSessionTime;
        ImageButton buttonSessionAction;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSessionTitle = itemView.findViewById(R.id.tvSessionTitle);
            tvSessionTime = itemView.findViewById(R.id.tvSessionTime);
            buttonSessionAction = itemView.findViewById(R.id.buttonSessionAction);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSessionClick(sessions.get(position));
                }
            });
        }

        void bind(Session session) {
            tvSessionTitle.setText(session.title);
            tvSessionTime.setText(formatTimestamp(session.updatedAt));

            // Thiết lập click listener cho nút action (3 chấm)
            buttonSessionAction.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(itemView.getContext(), buttonSessionAction);
                popupMenu.getMenuInflater().inflate(R.menu.session_actions_menu, popupMenu.getMenu());

                popupMenu.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_rename) {
                        if (actionListener != null) {
                            actionListener.onRenameSession(session);
                        }
                        return true;
                    } else if (itemId == R.id.action_duplicate) {
                        if (actionListener != null) {
                            actionListener.onDuplicateSession(session);
                        }
                        return true;
                    } else if (itemId == R.id.action_delete) {
                        if (actionListener != null) {
                            actionListener.onDeleteSession(session);
                        }
                        return true;
                    }
                    return false;
                });

                popupMenu.show();
            });
        }

        private String formatTimestamp(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }
    }
}