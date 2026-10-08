package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.syncore.data.local.entity.PaymentEntity;

import java.util.List;

@Dao
public interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(PaymentEntity payment);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertAll(List<PaymentEntity> payments);
    @Query("SELECT * FROM payments WHERE sale_id = :saleId ORDER BY received_at_epoch_ms") List<PaymentEntity> getForSale(String saleId);
}
