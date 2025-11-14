package com.example.aimap.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sessions")

public class Session {
    // Class này định nghĩa cấu trúc bảng Session trong cơ sở dữ liệu
    @PrimaryKey
    @NonNull
    public String session_id;
    public String title;
    public long createdAt;
    public long updatedAt;
    public boolean isActive;

    public Session(String session_id, String title, long createdAt, long updatedAt, boolean isActive) {
        this.session_id = session_id;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isActive = isActive;
    }
}
