package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;

import java.util.UUID;

/** Append-only stock movement. Its source reference is polymorphic, so it is represented by type plus ID. */
@Entity(tableName = "inventory_ledger",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ProductEntity.class, parentColumns = "product_id", childColumns = "product_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "actor_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "product_id", "occurred_at_epoch_ms"}),
                @Index(value = {"event_type", "source_type", "source_record_id", "product_id"}, unique = true),
                @Index(value = {"product_id"}),
                @Index(value = {"farm_id", "actor_user_id"}), @Index(value = {"farm_id", "device_id"})
        })
public class InventoryLedgerEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "ledger_entry_id") public String ledgerEntryId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    @ColumnInfo(name = "quantity_delta_eggs") public int quantityDeltaEggs;
    @NonNull @ColumnInfo(name = "event_type") public InventoryEventType eventType = InventoryEventType.HARVEST;
    @NonNull @ColumnInfo(name = "source_type") public String sourceType = "";
    @NonNull @ColumnInfo(name = "source_record_id") public String sourceRecordId = "";
    @ColumnInfo(name = "actor_user_id") public String actorUserId;
    @ColumnInfo(name = "device_id") public String deviceId;
    @ColumnInfo(name = "occurred_at_epoch_ms") public long occurredAtEpochMs;
}
