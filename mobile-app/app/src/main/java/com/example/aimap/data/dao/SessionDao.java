package com.example.aimap.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.aimap.data.Session;

import java.util.List;

@Dao
public interface SessionDao {

    @Insert
    void insertSession(Session session);

    @Update
    void updateSession(Session session);

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    Session getSessionById(String sessionId);

    @Query("DELETE FROM sessions WHERE session_id = :sessionId")
    void deleteSessionById(String sessionId);

    @Query("SELECT * FROM sessions ORDER BY last_updated DESC")
    List<Session> getAllSessions();

    @Query("SELECT COUNT(*) FROM sessions")
    int getSessionCount();
}
