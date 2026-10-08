package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** Sale line with immutable unit-price and line-total snapshots from the time of sale. */
@Entity(tableName = "sale_items",
        foreignKeys = {
                @ForeignKey(entity = SaleEntity.class, parentColumns = "sale_id", childColumns = "sale_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ProductEntity.class, parentColumns = "product_id", childColumns = "product_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = PriceVersionEntity.class, parentColumns = "price_version_id", childColumns = "price_version_id",
                        onDelete = ForeignKey.SET_NULL, onUpdate = ForeignKey.CASCADE)
        },
        indices = {
                @Index(value = {"sale_id", "product_id"}, unique = true),
                @Index(value = {"product_id"}), @Index(value = {"price_version_id"})
        })
public class SaleItemEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "sale_item_id") public String saleItemId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "sale_id") public String saleId = "";
    @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    @ColumnInfo(name = "price_version_id") public String priceVersionId;
    @ColumnInfo(name = "quantity_eggs") public int quantityEggs;
    @ColumnInfo(name = "unit_price_minor_units") public long unitPriceMinorUnits;
    @ColumnInfo(name = "line_total_minor_units") public long lineTotalMinorUnits;
}
