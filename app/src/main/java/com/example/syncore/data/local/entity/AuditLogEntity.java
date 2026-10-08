package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** Append-only record of an important action. Metadata is optional JSON text owned by the caller. */
@Entity(tableName = "audit_logs",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "actor_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "occurred_at_epoch_ms"}),
                @Index(value = {"affected_type", "affected_record_id"}),
                @Index(value = {"farm_id", "actor_user_id"}), @Index(value = {"farm_id", "device_id"})
        })
public class AuditLogEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "audit_log_id") public String auditLogId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @ColumnInfo(name = "actor_user_id") public String actorUserId;
    @ColumnInfo(name = "device_id") public String deviceId;
    @NonNull public String action = "";
    @NonNull @ColumnInfo(name = "affected_type") public String affectedType = "";
    @NonNull @ColumnInfo(name = "affected_record_id") public String affectedRecordId = "";
    @ColumnInfo(name = "occurred_at_epoch_ms") public long occurredAtEpochMs;
    @ColumnInfo(name = "details_json") public String detailsJson;
}
