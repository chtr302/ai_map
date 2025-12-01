package com.example.aimap.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "chat_messages")

public class ChatMessage {
    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_SUGGESTED_PLACE = 2;

    @PrimaryKey
    @NonNull
    public String message_id;
    public String session_id;
    public String message;
    public String metadata;
    public int type;
    public long timestamp;

    public ChatMessage(String message_id, String session_id, String message, String metadata, int type, long timestamp) {
        this.message_id = message_id;
        this.session_id = session_id;
        this.message = message;
        this.metadata = metadata;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getMessage() { return message; }
    public int getType() { return type; }

    @NonNull
    public String getMessage_id() {
        return message_id;
    }

    public String getSession_id() {
        return session_id;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
