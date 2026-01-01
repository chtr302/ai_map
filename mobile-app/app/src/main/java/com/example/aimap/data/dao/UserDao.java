package com.example.aimap.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.aimap.data.User;

@Dao
public interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Update
    void updateUser(User user);

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    User getUserById(String userId);

    @Query("SELECT * FROM users WHERE googleUserId = :googleUserId LIMIT 1")
    User getUserByGoogleId(String googleUserId);

    @Query("SELECT * FROM users WHERE isGuest = 1 LIMIT 1")
    User getGuestUser();

    @Query("DELETE FROM users WHERE userId = :userId")
    void deleteUserById(String userId);

    @Query("SELECT COUNT(*) FROM users")
    int getUserCount();
}


