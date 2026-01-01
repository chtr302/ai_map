package com.example.aimap.data;

// POJO luu thong tin user hien tai trong memory
public class UserSession {

    public static final String PROVIDER_GUEST = "GUEST";
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    public String userId;
    public String displayName;
    public String email;
    public String avatarUrl;
    public boolean isGuest;
    public String provider;
    public int questionCount; // So cau hoi da goi

    public UserSession(String userId,
                       String displayName,
                       String email,
                       String avatarUrl,
                       boolean isGuest,
                       String provider,
                       int questionCount) {
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.isGuest = isGuest;
        this.provider = provider;
        this.questionCount = questionCount;
    }
}


