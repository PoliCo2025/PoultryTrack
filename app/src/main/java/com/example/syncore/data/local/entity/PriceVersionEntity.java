package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;

import java.util.UUID;

/** Effective-dated price history. A new price is a new row; old sale snapshots remain unchanged. */
@Entity(tableName = "price_versions",
        foreignKeys = {
                @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ProductEntity.class, parentColumns = "product_id", childColumns = "product_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = UserEntity.class, parentColumns = {"farm_id", "user_id"}, childColumns = {"farm_id", "changed_by_user_id"},
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"farm_id", "product_id", "version_number"}, unique = true),
                @Index(value = {"farm_id", "product_id", "effective_from_epoch_ms"}),
                @Index(value = {"product_id"}),
                @Index(value = {"farm_id", "changed_by_user_id"})
        })
public class PriceVersionEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "price_version_id")
    public String priceVersionId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    /** Integer minor currency units (for PHP, centavos); never a floating-point amount. */
    @ColumnInfo(name = "amount_minor_units") public long amountMinorUnits;
    @NonNull @ColumnInfo(name = "currency_code") public String currencyCode = "PHP";
    @ColumnInfo(name = "effective_from_epoch_ms") public long effectiveFromEpochMs;
    @ColumnInfo(name = "effective_to_epoch_ms") public Long effectiveToEpochMs;
    @ColumnInfo(name = "version_number") public int versionNumber;
    @NonNull public PriceStatus status = PriceStatus.ACTIVE;
    @ColumnInfo(name = "changed_by_user_id") public String changedByUserId;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
}
