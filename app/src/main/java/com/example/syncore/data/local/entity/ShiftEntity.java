package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.ShiftStatus;

import java.util.UUID;

/** Cash-session header; all monetary fields use integer minor units. */
@Entity(tableName = "shifts",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = DeviceEntity.class, parentColumns = {"farm_id", "device_id"}, childColumns = {"farm_id", "device_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "started_at_epoch_ms"}),
                @Index(value = {"farm_id", "shift_id"}, unique = true),
                @Index(value = {"farm_id", "user_id"}), @Index(value = {"farm_id", "device_id"})
        })
public class ShiftEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "shift_id") public String shiftId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "user_id") public String userId = "";
    @ColumnInfo(name = "device_id") public String deviceId;
    @ColumnInfo(name = "started_at_epoch_ms") public long startedAtEpochMs;
    @ColumnInfo(name = "ended_at_epoch_ms") public Long endedAtEpochMs;
    @ColumnInfo(name = "opening_cash_minor_units") public long openingCashMinorUnits;
    @ColumnInfo(name = "closing_cash_minor_units") public Long closingCashMinorUnits;
    @ColumnInfo(name = "expected_cash_minor_units") public Long expectedCashMinorUnits;
    @ColumnInfo(name = "difference_cash_minor_units") public Long differenceCashMinorUnits;
    @NonNull public ShiftStatus status = ShiftStatus.OPEN;
}
