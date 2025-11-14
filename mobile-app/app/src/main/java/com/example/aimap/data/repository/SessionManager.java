package com.example.aimap.data.repository;

import com.example.aimap.data.ChatMessage;
import com.example.aimap.data.Session;
import com.example.aimap.data.dao.ChatMessageDao;
import com.example.aimap.data.dao.SessionDao;

import java.util.List;
import java.util.UUID;

public class SessionManager {
    private SessionDao sessionDao;
    private ChatMessageDao chatMessageDao;

    public SessionManager(SessionDao sessionDao, ChatMessageDao chatMessageDao) {
        this.sessionDao = sessionDao;
        this.chatMessageDao = chatMessageDao;
    }

    public String createNewSession(String title) {
        String sessionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        Session session = new Session(sessionId, title, now, now, true);
        sessionDao.insertSession(session);
        return sessionId;
    }
    public void addMessageToSession(String sessionId, String message, int type) {
        String messageId = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        ChatMessage chatMessage = new ChatMessage(messageId, sessionId, message, type, timestamp);
        chatMessageDao.insertMessage(chatMessage);
        sessionDao.updateSessionTimestamp(sessionId, timestamp);
    }
    public List<Session> getAllActiveSessions() {
        return sessionDao.getAllActiveSessions();
    }
    public List<ChatMessage> getMessagesBySession(String sessionId){
        return chatMessageDao.getMessageByeSession(sessionId);
    }
    public List<ChatMessage> getMessagesBySessionWithLimit(String sessionId, int limit, int offset){
        return chatMessageDao.getMessagesBySessionWithLimit(sessionId, limit, offset);
    }
    public void closeSession(String sessionId) {
        sessionDao.deactivateSession(sessionId);
    }
    public void deleteSession(String sessionId) {
        sessionDao.deleteSessionById(sessionId);
    }
    public void updateSessionTitle(String sessionId, String title) {
        sessionDao.updateSessionTitle(sessionId, title);
    }
    public Session getSessionById(String sessionId) {
        return sessionDao.getSessionById(sessionId);
    }
}
