package com.example.aimap.data;

// Thong tin user dang su dung app
public class UserSession {

    public static final String PROVIDER_GUEST = "GUEST";
    public static final String PROVIDER_GOOGLE = "GOOGLE";

    public String userId;
    public String displayName;
    public String email;
    public String avatarUrl;
    public boolean isGuest;
    public String provider;

    public UserSession(String userId,
                       String displayName,
                       String email,
                       String avatarUrl,
                       boolean isGuest,
                       String provider) {
        this.userId = userId;
        this.displayName = displayName;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.isGuest = isGuest;
        this.provider = provider;
    }
}

