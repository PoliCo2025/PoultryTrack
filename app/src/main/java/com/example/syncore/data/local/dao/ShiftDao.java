package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.ShiftEntity;

import java.util.List;

@Dao
public interface ShiftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(ShiftEntity shift);
    @Update int update(ShiftEntity shift);
    @Query("SELECT * FROM shifts WHERE shift_id = :shiftId LIMIT 1") ShiftEntity findById(String shiftId);
    @Query("SELECT * FROM shifts WHERE farm_id = :farmId ORDER BY started_at_epoch_ms DESC") List<ShiftEntity> getForFarm(String farmId);
}
