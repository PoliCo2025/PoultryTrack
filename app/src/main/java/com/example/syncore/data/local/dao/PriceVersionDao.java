package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.PriceVersionEntity;

import java.util.List;

@Dao
public interface PriceVersionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(PriceVersionEntity price);
    @Update int update(PriceVersionEntity price);
    @Query("SELECT * FROM price_versions WHERE price_version_id = :priceVersionId LIMIT 1") PriceVersionEntity findById(String priceVersionId);
    @Query("SELECT * FROM price_versions WHERE farm_id = :farmId AND product_id = :productId " +
            "AND status = 'ACTIVE' AND effective_from_epoch_ms <= :atEpochMs " +
            "AND (effective_to_epoch_ms IS NULL OR effective_to_epoch_ms > :atEpochMs) " +
            "ORDER BY version_number DESC LIMIT 1")
    PriceVersionEntity findEffectiveAt(String farmId, String productId, long atEpochMs);
    @Query("SELECT * FROM price_versions WHERE farm_id = :farmId AND product_id = :productId " +
            "ORDER BY version_number DESC")
    List<PriceVersionEntity> getHistory(String farmId, String productId);
    @Query("SELECT * FROM price_versions WHERE farm_id = :farmId AND product_id = :productId " +
            "ORDER BY version_number DESC LIMIT 1")
    PriceVersionEntity getLatest(String farmId, String productId);
}
