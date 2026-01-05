package com.example.aimap.data;

import android.content.Context;
import android.content.SharedPreferences;

public class SettingsManager {
    private static final String PREF_NAME = "app_settings";
    private static final String KEY_DARK_THEME = "dark_theme";
    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_THINKING = "thinking_enabled";

    public static final String LANG_VI = "vi";
    public static final String LANG_EN = "en";

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public boolean isDarkThemeEnabled() {
        return prefs.getBoolean(KEY_DARK_THEME, false); // Mặc định là Light theme
    }

    public void setDarkThemeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_THEME, enabled).apply();
    }

    public String getLanguage() {
        return prefs.getString(KEY_LANGUAGE, LANG_VI);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(KEY_LANGUAGE, language).apply();
    }

    public boolean isThinkingEnabled() {
        return prefs.getBoolean(KEY_THINKING, true);
    }

    public void setThinkingEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_THINKING, enabled).apply();
    }
}