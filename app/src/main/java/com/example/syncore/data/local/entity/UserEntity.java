package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.syncore.data.local.entity.DatabaseEnums.UserRole;

import java.util.UUID;

/** Farm-scoped staff account metadata. Authentication secrets intentionally do not belong here. */
@Entity(tableName = "users",
        foreignKeys = @ForeignKey(entity = FarmEntity.class, parentColumns = "farm_id", childColumns = "farm_id",
                onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"farm_id"}),
                @Index(value = {"username_normalized"}, unique = true),
                @Index(value = {"farm_id", "user_id"}, unique = true)
        })
public class UserEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "user_id")
    public String userId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "farm_id") public String farmId = "";
    @NonNull public String username = "";
    /** Lowercase/trimmed username used for case-insensitive uniqueness and lookup. */
    @NonNull @ColumnInfo(name = "username_normalized") public String usernameNormalized = "";
    @NonNull @ColumnInfo(name = "display_name") public String displayName = "";
    @NonNull public UserRole role = UserRole.SALES_PERSONNEL;
    @ColumnInfo(name = "is_active") public boolean active = true;
    @ColumnInfo(name = "created_at_epoch_ms") public long createdAtEpochMs = System.currentTimeMillis();
    @ColumnInfo(name = "updated_at_epoch_ms") public long updatedAtEpochMs = createdAtEpochMs;
}
