package com.example.aimap.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

// Quan ly thong tin user hien tai, luu trong SharedPreferences
public class SessionManager {

    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "user_session_prefs";
    private static final String KEY_USER = "user_json";

    private static SessionManager instance;

    private final SharedPreferences prefs;
    private UserSession currentUser;

    private SessionManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    // Lay user hien tai, neu chua co thi tao guest
    public UserSession getCurrentUser() {
        if (currentUser == null) {
            loadFromPrefs();
        }
        if (currentUser == null) {
            currentUser = createGuestSessionInternal();
            saveToPrefs();
        }
        return currentUser;
    }

    // Set user moi (sau khi login Google)
    public void setCurrentUser(UserSession user) {
        this.currentUser = user;
        saveToPrefs();
    }

    // Tao lai user guest (sau khi dang xuat)
    public UserSession createGuestSession() {
        currentUser = createGuestSessionInternal();
        saveToPrefs();
        return currentUser;
    }

    private UserSession createGuestSessionInternal() {
        String guestId = "guest-" + UUID.randomUUID().toString();
        return new UserSession(
                guestId,
                "Khach",
                null,
                null,
                true,
                UserSession.PROVIDER_GUEST
        );
    }

    private void loadFromPrefs() {
        String json = prefs.getString(KEY_USER, null);
        if (json == null) {
            return;
        }
        try {
            JSONObject obj = new JSONObject(json);
            String userId = obj.optString("userId", null);
            String displayName = obj.optString("displayName", null);
            String email = obj.optString("email", null);
            String avatarUrl = obj.optString("avatarUrl", null);
            boolean isGuest = obj.optBoolean("isGuest", true);
            String provider = obj.optString("provider", UserSession.PROVIDER_GUEST);

            if (userId != null) {
                currentUser = new UserSession(
                        userId,
                        displayName,
                        email,
                        avatarUrl,
                        isGuest,
                        provider
                );
            }
        } catch (JSONException e) {
            Log.e(TAG, "loadFromPrefs error", e);
        }
    }

    private void saveToPrefs() {
        SharedPreferences.Editor editor = prefs.edit();
        if (currentUser == null) {
            editor.remove(KEY_USER).apply();
            return;
        }
        try {
            JSONObject obj = new JSONObject();
            obj.put("userId", currentUser.userId);
            obj.put("displayName", currentUser.displayName);
            obj.put("email", currentUser.email);
            obj.put("avatarUrl", currentUser.avatarUrl);
            obj.put("isGuest", currentUser.isGuest);
            obj.put("provider", currentUser.provider);
            editor.putString(KEY_USER, obj.toString());
        } catch (JSONException e) {
            Log.e(TAG, "saveToPrefs error", e);
        }
        editor.apply();
    }
}

