package com.example.aimap.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")
public class Session {

    @PrimaryKey
    @NonNull
    public String session_id;

    public String userId;           // ID cua user so huu session nay
    public String title;            // tên session (auto từ câu đầu, có thể rename)
    public String preview_message;  // câu gần nhất
    public long last_updated;       // millis
    public boolean isPinned;        // co pin len dau khong

    public Session(@NonNull String session_id,
                   String userId,
                   String title,
                   String preview_message,
                   long last_updated,
                   boolean isPinned) {
        this.session_id = session_id;
        this.userId = userId;
        this.title = title;
        this.preview_message = preview_message;
        this.last_updated = last_updated;
        this.isPinned = isPinned;
    }

    @NonNull
    public String getSession_id() {
        return session_id;
    }

    public String getTitle() {
        return title;
    }

    public String getPreview_message() {
        return preview_message;
    }

    public long getLast_updated() {
        return last_updated;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setPreview_message(String preview_message) {
        this.preview_message = preview_message;
    }

    public void setLast_updated(long last_updated) {
        this.last_updated = last_updated;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }
}
