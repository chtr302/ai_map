package com.example.aimap.data;

import android.content.Context;
import android.content.SharedPreferences;

// Quản lý cấu hình ứng dụng
public class SettingsManager {
    private static final String PREF_NAME = "app_settings";
    private static final String KEY_THEME = "theme_mode";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THINKING = "ai_thinking_enabled";

    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    public static final String LANG_VI = "vi";
    public static final String LANG_EN = "en";

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    // Theme
    public boolean isDarkThemeEnabled() {
        return THEME_DARK.equals(prefs.getString(KEY_THEME, THEME_LIGHT));
    }

    public void setDarkThemeEnabled(boolean enabled) {
        prefs.edit().putString(KEY_THEME, enabled ? THEME_DARK : THEME_LIGHT).apply();
    }

    // Language
    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANG_VI);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    // AI Thinking
    public boolean isThinkingEnabled() {
        return prefs.getBoolean(KEY_THINKING, false);
    }

    public void setThinkingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_THINKING, enabled).apply();
    }
}
