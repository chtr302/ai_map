package com.example.aimap.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.aimap.data.dao.ChatMessageDao;
import com.example.aimap.data.dao.SessionDao;

@Database(
        entities = {ChatMessage.class, Session.class},
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    public abstract ChatMessageDao chatMessageDao();
    public abstract SessionDao sessionDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context){
        if (INSTANCE == null){
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "chat_database"
                            ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
