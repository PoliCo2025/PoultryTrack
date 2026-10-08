package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.OutboxOperation;
import com.example.syncore.data.local.entity.DatabaseEnums.SyncStatus;

import java.util.UUID;

/** Persistence envelope for future synchronization. This entity does not execute or schedule sync. */
@Entity(tableName = "sync_outbox",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "sync_status", "created_at_epoch_ms"}),
                @Index(value = {"idempotency_key"}, unique = true),
                @Index(value = {"record_type", "record_id"}), @Index(value = {"farm_id", "device_id"})
        })
public class SyncOutboxEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "outbox_id") public String outboxId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @ColumnInfo(name = "device_id") public String deviceId;
    @NonNull @ColumnInfo(name = "record_type") public String recordType = "";
    @NonNull @ColumnInfo(name = "record_id") public String recordId = "";
    @NonNull @ColumnInfo(name = "operation") public OutboxOperation operation = OutboxOperation.CREATE;
    @NonNull @ColumnInfo(name = "sync_status") public SyncStatus syncStatus = SyncStatus.PENDING;
    @ColumnInfo(name = "retry_count") public int retryCount;
    @ColumnInfo(name = "last_error") public String lastError;
    @NonNull @ColumnInfo(name = "idempotency_key") public String idempotencyKey = UUID.randomUUID().toString();
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "last_attempted_at_epoch_ms") public Long lastAttemptedAtEpochMs;
}
