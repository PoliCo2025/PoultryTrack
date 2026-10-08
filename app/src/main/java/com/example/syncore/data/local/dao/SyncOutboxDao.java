package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.DatabaseEnums.SyncStatus;
import com.example.syncore.data.local.entity.SyncOutboxEntity;

import java.util.List;

@Dao
public interface SyncOutboxDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(SyncOutboxEntity operation);
    @Update int update(SyncOutboxEntity operation);
    @Query("SELECT * FROM sync_outbox WHERE outbox_id = :outboxId LIMIT 1") SyncOutboxEntity findById(String outboxId);
    @Query("SELECT * FROM sync_outbox WHERE farm_id = :farmId AND sync_status = :status " +
            "ORDER BY created_at_epoch_ms, outbox_id")
    List<SyncOutboxEntity> getByStatus(String farmId, SyncStatus status);
}
