package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** A tenant boundary for farm-owned operational records. */
@Entity(tableName = "farms", indices = {@Index(value = {"name"})})
public class FarmEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "farm_id")
    public String farmId = UUID.randomUUID().toString();

    @NonNull public String name = "";
    public String location;
    @ColumnInfo(name = "is_active") public boolean active = true;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "updated_at_epoch_ms") public long updatedAtEpochMs = createdAtEpochMs;
}
