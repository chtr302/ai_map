package com.example.aimap.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.aimap.data.ChatMessage;

import java.util.List;

@Dao
public interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    List<ChatMessage> getMessageByeSession(String sessionId);

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    List<ChatMessage> getMessagesBySessionWithLimit(String sessionId, int limit, int offset);

    @Insert
    void insertMessage(ChatMessage message);

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId")
    void deleteMessagesBySession(String sessionId);

    @Query("DELETE FROM chat_messages WHERE session_id = :sessionId AND message_id = :messageId")
    void deleteMessageById(String sessionId, String messageId);
}
