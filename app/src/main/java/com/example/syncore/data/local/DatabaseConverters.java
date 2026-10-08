package com.example.syncore.data.local;

import androidx.room.TypeConverter;

import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.HarvestStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.DatabaseEnums.OutboxOperation;
import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.SaleStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.ShiftStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.SyncStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.UserRole;

/** Stores enum values as stable, readable strings instead of ordinal numbers. */
public final class DatabaseConverters {
    private DatabaseConverters() { }

    @TypeConverter public static String fromUserRole(UserRole value) { return value == null ? null : value.name(); }
    @TypeConverter public static UserRole toUserRole(String value) { return value == null ? null : UserRole.valueOf(value); }
    @TypeConverter public static String fromPriceStatus(PriceStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static PriceStatus toPriceStatus(String value) { return value == null ? null : PriceStatus.valueOf(value); }
    @TypeConverter public static String fromShiftStatus(ShiftStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static ShiftStatus toShiftStatus(String value) { return value == null ? null : ShiftStatus.valueOf(value); }
    @TypeConverter public static String fromSaleStatus(SaleStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static SaleStatus toSaleStatus(String value) { return value == null ? null : SaleStatus.valueOf(value); }
    @TypeConverter public static String fromHarvestStatus(HarvestStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static HarvestStatus toHarvestStatus(String value) { return value == null ? null : HarvestStatus.valueOf(value); }
    @TypeConverter public static String fromAdjustmentStatus(AdjustmentStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static AdjustmentStatus toAdjustmentStatus(String value) { return value == null ? null : AdjustmentStatus.valueOf(value); }
    @TypeConverter public static String fromInventoryEventType(InventoryEventType value) { return value == null ? null : value.name(); }
    @TypeConverter public static InventoryEventType toInventoryEventType(String value) { return value == null ? null : InventoryEventType.valueOf(value); }
    @TypeConverter public static String fromOutboxOperation(OutboxOperation value) { return value == null ? null : value.name(); }
    @TypeConverter public static OutboxOperation toOutboxOperation(String value) { return value == null ? null : OutboxOperation.valueOf(value); }
    @TypeConverter public static String fromSyncStatus(SyncStatus value) { return value == null ? null : value.name(); }
    @TypeConverter public static SyncStatus toSyncStatus(String value) { return value == null ? null : SyncStatus.valueOf(value); }
}
