package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.SaleStatus;

import java.util.UUID;

/** Sale/receipt header. Calculations and state transitions belong to a later business-logic phase. */
@Entity(tableName = "sales",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "actor_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ShiftEntity.class, parentColumns = {"farm_id", "shift_id"}, childColumns = {"farm_id", "shift_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"receipt_number"}, unique = true),
                @Index(value = {"farm_id", "sale_id"}, unique = true),
                @Index(value = {"farm_id", "sold_at_epoch_ms"}),
                @Index(value = {"farm_id", "actor_user_id"}),
                @Index(value = {"farm_id", "shift_id"}),
                @Index(value = {"farm_id", "device_id"})
        })
public class SaleEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "sale_id") public String saleId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "receipt_number") public String receiptNumber = "";
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "actor_user_id") public String actorUserId = "";
    @ColumnInfo(name = "shift_id") public String shiftId;
    @ColumnInfo(name = "device_id") public String deviceId;
    @ColumnInfo(name = "sold_at_epoch_ms") public long soldAtEpochMs;
    @ColumnInfo(name = "subtotal_minor_units") public long subtotalMinorUnits;
    @ColumnInfo(name = "total_minor_units") public long totalMinorUnits;
    @ColumnInfo(name = "cash_received_minor_units") public Long cashReceivedMinorUnits;
    @ColumnInfo(name = "change_minor_units") public Long changeMinorUnits;
    @NonNull @ColumnInfo(name = "currency_code") public String currencyCode = "PHP";
    @NonNull public SaleStatus status = SaleStatus.DRAFT;
}
