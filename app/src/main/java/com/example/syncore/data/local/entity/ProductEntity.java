package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Catalog item, currently used for the Small, Medium, Large, and Extra Large egg sizes. */
@Entity(tableName = "products", indices = {@Index(value = {"name"}, unique = true)})
public class ProductEntity {
    public static final String SMALL_ID = "egg-small";
    public static final String MEDIUM_ID = "egg-medium";
    public static final String LARGE_ID = "egg-large";
    public static final String EXTRA_LARGE_ID = "egg-extra-large";

    @PrimaryKey @NonNull @ColumnInfo(name = "product_id") public String productId = "";
    @NonNull public String name = "";
    @NonNull public String unit = "egg";
    @ColumnInfo(name = "sort_order") public int sortOrder;
    @ColumnInfo(name = "is_active") public boolean active = true;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "updated_at_epoch_ms") public long updatedAtEpochMs = createdAtEpochMs;
}
