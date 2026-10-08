package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.DeviceEntity;

import java.util.List;

@Dao
public interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(DeviceEntity device);
    @Update int update(DeviceEntity device);
    @Query("SELECT * FROM devices WHERE device_id = :deviceId LIMIT 1") DeviceEntity findById(String deviceId);
    @Query("SELECT * FROM devices WHERE farm_id = :farmId ORDER BY device_name") List<DeviceEntity> getForFarm(String farmId);
}
