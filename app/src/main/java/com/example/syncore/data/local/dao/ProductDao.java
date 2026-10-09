package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.syncore.data.local.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) void insertCatalogIfMissing(List<ProductEntity> products);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(ProductEntity product);
    @Update int update(ProductEntity product);
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY sort_order, name") List<ProductEntity> getActiveProducts();
    @Query("SELECT * FROM products ORDER BY sort_order, name") List<ProductEntity> getAllProducts();
    @Query("SELECT * FROM products WHERE product_id = :productId LIMIT 1") ProductEntity findById(String productId);
}
