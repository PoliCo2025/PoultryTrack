package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.syncore.data.local.entity.SaleItemEntity;

import java.util.List;

@Dao
public interface SaleItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(SaleItemEntity item);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertAll(List<SaleItemEntity> items);
    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId ORDER BY sale_item_id") List<SaleItemEntity> getForSale(String saleId);
}
