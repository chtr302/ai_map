package com.example.aimap.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {

    @PrimaryKey
    @NonNull
    public String userId; // guest-<uuid> hoac google-<googleId>

    public String googleUserId; // ID tu Google
    public String displayName;
    public String email;
    public String avatarUrl; // URL anh dai dien tu Google
    public boolean isGuest;
    public int questionCount; // So cau hoi da goi (cho guest)
    public long createdAt;
    public long updatedAt;

    public User(@NonNull String userId,
                String googleUserId,
                String displayName,
                String email,
                String avatarUrl,
                boolean isGuest,
                int questionCount,
                long createdAt,
                long updatedAt) {
        this.userId = userId;
        this.googleUserId = googleUserId;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.isGuest = isGuest;
        this.questionCount = questionCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public String getGoogleUserId() {
        return googleUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isGuest() {
        return isGuest;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}


