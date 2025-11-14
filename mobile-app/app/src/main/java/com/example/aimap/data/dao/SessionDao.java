package com.example.aimap.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.aimap.data.Session;

import java.util.List;

@Dao
public interface SessionDao {
    @Query("SELECT * FROM sessions WHERE isActive = 1 ORDER BY updatedAt DESC")
    List<Session> getAllActiveSessions();

    @Query("SELECT * FROM sessions WHERE session_id = :session_id")
    Session getSessionById(String session_id);

    @Insert
    void insertSession(Session session);

    @Update
    void updateSession(Session session);

    @Query("UPDATE sessions SET updatedAt = :timestamp WHERE session_id = :sessionId")
    void updateSessionTimestamp(String sessionId, long timestamp);

    @Query("UPDATE sessions SET isActive = 0 WHERE session_id = :sessionId")
    void deactivateSession(String sessionId);

    @Query("UPDATE sessions SET title = :title WHERE session_id = :sessionId")
    void updateSessionTitle(String sessionId, String title);

    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    void deleteSessionById(String sessionId);
}
