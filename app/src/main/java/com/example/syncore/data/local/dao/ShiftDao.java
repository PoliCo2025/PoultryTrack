package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.ShiftEntity;

import java.util.List;

@Dao
public interface ShiftDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(ShiftEntity shift);
    @Update int update(ShiftEntity shift);
    @Query("SELECT * FROM shifts WHERE shift_id = :shiftId LIMIT 1") ShiftEntity findById(String shiftId);
    @Query("SELECT * FROM shifts WHERE farm_id = :farmId ORDER BY started_at_epoch_ms DESC") List<ShiftEntity> getForFarm(String farmId);
    @Query("SELECT * FROM shifts WHERE farm_id = :farmId AND user_id = :userId AND status = 'OPEN' LIMIT 1")
    ShiftEntity findOpenForUser(String farmId, String userId);
    @Query("SELECT COALESCE(SUM(p.amount_minor_units), 0) FROM payments p " +
            "INNER JOIN sales s ON s.sale_id = p.sale_id " +
            "WHERE s.farm_id = :farmId AND s.shift_id = :shiftId " +
            "AND s.status = 'COMPLETED' AND p.payment_method = 'CASH' AND p.currency_code = :currencyCode")
    long getCashPaidForShift(String farmId, String shiftId, String currencyCode);
    @Query("SELECT MAX(sold_at_epoch_ms) FROM sales WHERE farm_id = :farmId AND shift_id = :shiftId AND status = 'COMPLETED'")
    Long getLatestCompletedSaleTime(String farmId, String shiftId);
}
