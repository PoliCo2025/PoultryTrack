package com.example.syncore.data.repository;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;

import java.util.List;

/** Storage-only sale aggregate writer. It performs no totals, stock, payment, or status business rules. */
public final class SaleRepository {
    private final PoultryTrackDatabase database;

    public SaleRepository(PoultryTrackDatabase database) {
        this.database = database;
    }

    /** Persists a prebuilt sale, its price snapshots, and payments atomically. */
    public void save(SaleEntity sale, List<SaleItemEntity> items, List<PaymentEntity> payments) {
        database.runInTransaction(() -> {
            database.saleDao().insert(sale);
            if (items != null && !items.isEmpty()) database.saleItemDao().insertAll(items);
            if (payments != null && !payments.isEmpty()) database.paymentDao().insertAll(payments);
        });
    }
}
