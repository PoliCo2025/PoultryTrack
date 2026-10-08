package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.UserEntity;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(UserEntity user);
    @Update int update(UserEntity user);
    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1") UserEntity findById(String userId);
    @Query("SELECT * FROM users WHERE username_normalized = :usernameNormalized LIMIT 1") UserEntity findByNormalizedUsername(String usernameNormalized);
    @Query("SELECT * FROM users WHERE farm_id = :farmId ORDER BY display_name") List<UserEntity> getForFarm(String farmId);
}
