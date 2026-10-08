package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.syncore.data.local.entity.AuditLogEntity;

import java.util.List;

@Dao
public interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(AuditLogEntity entry);
    @Query("SELECT * FROM audit_logs WHERE audit_log_id = :auditLogId LIMIT 1") AuditLogEntity findById(String auditLogId);
    @Query("SELECT * FROM audit_logs WHERE farm_id = :farmId ORDER BY occurred_at_epoch_ms DESC, audit_log_id DESC")
    List<AuditLogEntity> getForFarm(String farmId);
    @Query("SELECT * FROM audit_logs WHERE affected_type = :type AND affected_record_id = :recordId " +
            "ORDER BY occurred_at_epoch_ms DESC")
    List<AuditLogEntity> getForRecord(String type, String recordId);
}
