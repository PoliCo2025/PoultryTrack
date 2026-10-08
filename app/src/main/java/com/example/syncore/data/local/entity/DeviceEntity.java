package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** Installation identity used to attribute local records and future outbox operations. */
@Entity(tableName = "devices",
        foreignKeys = @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        indices = {@Index(value = {"farm_id"}), @Index(value = {"farm_id", "device_id"}, unique = true)})
public class DeviceEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "device_id")
    public String deviceId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "device_name") public String deviceName = "";
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "last_seen_at_epoch_ms") public Long lastSeenAtEpochMs;
}
