package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.HarvestStatus;

import java.util.UUID;

/** Harvest record header; inventory posting is intentionally outside this persistence model. */
@Entity(tableName = "harvests",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "actor_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "harvested_at_epoch_ms"}),
                @Index(value = {"farm_id", "actor_user_id"}),
                @Index(value = {"farm_id", "device_id"})
        })
public class HarvestEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "harvest_id") public String harvestId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "actor_user_id") public String actorUserId = "";
    @ColumnInfo(name = "device_id") public String deviceId;
    @ColumnInfo(name = "harvested_at_epoch_ms") public long harvestedAtEpochMs;
    public String notes;
    @NonNull public HarvestStatus status = HarvestStatus.RECORDED;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "updated_at_epoch_ms") public long updatedAtEpochMs = createdAtEpochMs;
}
