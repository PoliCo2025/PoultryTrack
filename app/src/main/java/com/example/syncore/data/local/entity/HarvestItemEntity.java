package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** Quantity of one egg size in a harvest. */
@Entity(tableName = "harvest_items",
        foreignKeys = {
                @ForeignKey(entity = HarvestEntity.class, parentColumns = "harvest_id", childColumns = "harvest_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
                @ForeignKey(entity = ProductEntity.class, parentColumns = "product_id", childColumns = "product_id",
                        onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE)
        },
        indices = {@Index(value = {"harvest_id", "product_id"}, unique = true), @Index(value = {"product_id"})})
public class HarvestItemEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "harvest_item_id") public String harvestItemId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "harvest_id") public String harvestId = "";
    @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    @ColumnInfo(name = "quantity_eggs") public int quantityEggs;
}
