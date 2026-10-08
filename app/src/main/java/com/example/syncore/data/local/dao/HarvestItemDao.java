package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.syncore.data.local.entity.HarvestItemEntity;

import java.util.List;

@Dao
public interface HarvestItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(HarvestItemEntity item);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertAll(List<HarvestItemEntity> items);
    @Query("SELECT * FROM harvest_items WHERE harvest_id = :harvestId ORDER BY product_id") List<HarvestItemEntity> getForHarvest(String harvestId);
}
