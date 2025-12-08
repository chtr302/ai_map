package com.example.aimap.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.aimap.R;
import com.example.aimap.data.Session;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.SessionViewHolder> {

    public interface OnSessionClickListener {
        void onSessionClick(Session session);      // click: load session
        void onSessionMenuClick(Session session);  // click nút ...: rename / xóa
    }

    private final List<Session> sessions = new ArrayList<>();
    private final OnSessionClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());

    public SessionAdapter(OnSessionClickListener listener) {
        this.listener = listener;
    }

    public void setSessions(List<Session> newSessions) {
        sessions.clear();
        if (newSessions != null) {
            sessions.addAll(newSessions);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SessionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new SessionViewHolder(view);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull SessionViewHolder holder, int position) {
        Session session = sessions.get(position);

        String title = (session.title == null || session.title.trim().isEmpty())
                ? "Cuộc trò chuyện mới"
                : session.title;
        String preview = (session.preview_message == null || session.preview_message.trim().isEmpty())
                ? "Bắt đầu trò chuyện..."
                : session.preview_message;

        holder.textSessionTitle.setText(title);
        holder.textSessionPreview.setText(preview);
        holder.textSessionDate.setText(
                dateFormat.format(new Date(session.last_updated))
        );

        // Click item -> load session
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onSessionClick(session);
        });

        // Click nút ... -> mở menu đổi tên / xóa
        holder.buttonSessionMenu.setOnClickListener(v -> {
            if (listener != null) listener.onSessionMenuClick(session);
        });
    }

    @Override
    public int getItemCount() {
        return sessions.size();
    }

    static class SessionViewHolder extends RecyclerView.ViewHolder {
        TextView textSessionTitle;
        TextView textSessionPreview;
        TextView textSessionDate;
        ImageButton buttonSessionMenu;

        SessionViewHolder(@NonNull View itemView) {
            super(itemView);
            textSessionTitle = itemView.findViewById(R.id.textSessionTitle);
            textSessionPreview = itemView.findViewById(R.id.textSessionPreview);
            textSessionDate = itemView.findViewById(R.id.textSessionDate);
            buttonSessionMenu = itemView.findViewById(R.id.buttonSessionMenu);
        }
    }
}
