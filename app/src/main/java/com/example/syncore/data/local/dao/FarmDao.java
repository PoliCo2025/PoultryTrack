package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.FarmEntity;

import java.util.List;

@Dao
public interface FarmDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(FarmEntity farm);
    @Update int update(FarmEntity farm);
    @Query("SELECT * FROM farms WHERE farm_id = :farmId LIMIT 1") FarmEntity findById(String farmId);
    @Query("SELECT * FROM farms WHERE is_active = 1 ORDER BY name") List<FarmEntity> getActiveFarms();
}
