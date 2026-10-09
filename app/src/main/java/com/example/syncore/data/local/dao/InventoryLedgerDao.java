package com.example.syncore.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.syncore.data.local.entity.InventoryBalance;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;

import java.util.List;

@Dao
public interface InventoryLedgerDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) void insert(InventoryLedgerEntity entry);
    @Insert(onConflict = OnConflictStrategy.ABORT) void insertAll(List<InventoryLedgerEntity> entries);
    @Query("SELECT * FROM inventory_ledger WHERE farm_id = :farmId AND product_id = :productId " +
            "ORDER BY occurred_at_epoch_ms DESC, ledger_entry_id DESC")
    List<InventoryLedgerEntity> getHistory(String farmId, String productId);
    @Query("SELECT p.product_id AS productId, p.name AS productName, " +
            "COALESCE(SUM(l.quantity_delta_eggs), 0) AS quantityOnHand " +
            "FROM products p LEFT JOIN inventory_ledger l " +
            "ON l.product_id = p.product_id AND l.farm_id = :farmId " +
            "WHERE p.is_active = 1 GROUP BY p.product_id, p.name ORDER BY p.sort_order, p.name")
    List<InventoryBalance> getBalances(String farmId);
    @Query("SELECT COALESCE(SUM(quantity_delta_eggs), 0) FROM inventory_ledger " +
            "WHERE farm_id = :farmId AND product_id = :productId")
    long getBalance(String farmId, String productId);
}
