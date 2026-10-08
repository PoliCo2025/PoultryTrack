package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.HarvestEntity;

import java.util.List;

@Dao
public interface HarvestDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(HarvestEntity harvest);
    @Update int update(HarvestEntity harvest);
    @Query("SELECT * FROM harvests WHERE harvest_id = :harvestId LIMIT 1") HarvestEntity findById(String harvestId);
    @Query("SELECT * FROM harvests WHERE farm_id = :farmId ORDER BY harvested_at_epoch_ms DESC") List<HarvestEntity> getForFarm(String farmId);
}
