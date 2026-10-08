package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.SaleEntity;

import java.util.List;

@Dao
public interface SaleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(SaleEntity sale);
    @Update int update(SaleEntity sale);
    @Query("SELECT * FROM sales WHERE sale_id = :saleId LIMIT 1") SaleEntity findById(String saleId);
    @Query("SELECT * FROM sales WHERE receipt_number = :receiptNumber LIMIT 1") SaleEntity findByReceiptNumber(String receiptNumber);
    @Query("SELECT * FROM sales WHERE farm_id = :farmId ORDER BY sold_at_epoch_ms DESC") List<SaleEntity> getForFarm(String farmId);
}
