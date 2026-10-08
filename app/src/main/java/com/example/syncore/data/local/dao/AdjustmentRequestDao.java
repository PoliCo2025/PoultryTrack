package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;

import java.util.List;

@Dao
public interface AdjustmentRequestDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(AdjustmentRequestEntity request);
    @Update int update(AdjustmentRequestEntity request);
    @Query("SELECT * FROM adjustment_requests WHERE adjustment_request_id = :requestId LIMIT 1") AdjustmentRequestEntity findById(String requestId);
    @Query("SELECT * FROM adjustment_requests WHERE farm_id = :farmId AND status = :status " +
            "ORDER BY requested_at_epoch_ms DESC")
    List<AdjustmentRequestEntity> getForFarmByStatus(String farmId, AdjustmentStatus status);
    @Query("SELECT * FROM adjustment_requests WHERE farm_id = :farmId ORDER BY requested_at_epoch_ms DESC")
    List<AdjustmentRequestEntity> getForFarm(String farmId);
}
