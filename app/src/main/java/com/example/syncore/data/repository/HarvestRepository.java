package com.example.syncore.data.repository;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.HarvestItemEntity;

import java.util.List;

/** Storage-only harvest aggregate writer. Inventory posting is deliberately not performed here. */
public final class HarvestRepository {
    private final PoultryTrackDatabase database;

    public HarvestRepository(PoultryTrackDatabase database) {
        this.database = database;
    }

    /** Persists a prebuilt harvest and its egg-size rows atomically. */
    public void save(HarvestEntity harvest, List<HarvestItemEntity> items) {
        database.runInTransaction(() -> {
            database.harvestDao().insert(harvest);
            if (items != null && !items.isEmpty()) database.harvestItemDao().insertAll(items);
        });
    }
}
