package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;

import java.util.UUID;

/** Proposed stock correction and its review metadata; approval behavior is a later phase. */
@Entity(tableName = "adjustment_requests",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "requested_by_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "reviewed_by_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ProductEntity.class, parentColumns = "product_id", childColumns = "product_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "status", "requested_at_epoch_ms"}),
                @Index(value = {"farm_id", "requested_by_user_id"}),
                @Index(value = {"farm_id", "reviewed_by_user_id"}),
                @Index(value = {"product_id"}), @Index(value = {"farm_id", "device_id"})
        })
public class AdjustmentRequestEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "adjustment_request_id") public String adjustmentRequestId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "requested_by_user_id") public String requestedByUserId = "";
    @ColumnInfo(name = "reviewed_by_user_id") public String reviewedByUserId;
    @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    @ColumnInfo(name = "device_id") public String deviceId;
    @ColumnInfo(name = "quantity_delta_eggs") public int quantityDeltaEggs;
    @NonNull public String reason = "";
    @NonNull public AdjustmentStatus status = AdjustmentStatus.PENDING;
    @ColumnInfo(name = "requested_at_epoch_ms") public long requestedAtEpochMs;
    @ColumnInfo(name = "reviewed_at_epoch_ms") public Long reviewedAtEpochMs;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "updated_at_epoch_ms") public long updatedAtEpochMs = createdAtEpochMs;
}
