package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.DatabaseEnums.HarvestStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.HarvestItemEntity;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.ProductEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Posts recorded egg collections and positive stock movements in one transaction. */
public final class HarvestService {
    private final PoultryTrackDatabase db;
    public HarvestService(PoultryTrackDatabase db) { this.db = db; }

    public HarvestEntity record(String harvestId, String farmId, String actorUserId, String deviceId,
                                long harvestedAtEpochMs, Map<String, Integer> quantities, String notes) {
        if (harvestId == null || harvestId.trim().isEmpty()) throw new IllegalArgumentException("Harvest id is required.");
        if (quantities == null || quantities.isEmpty()) throw new IllegalArgumentException("Harvest cannot be empty.");
        if (harvestedAtEpochMs < 0) throw new IllegalArgumentException("Harvest time cannot be negative.");
        final HarvestEntity[] result = new HarvestEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId); BusinessRules.user(db, farmId, actorUserId); BusinessRules.device(db, farmId, deviceId);
            if (db.harvestDao().findById(harvestId) != null) throw new IllegalStateException("This harvest has already been recorded.");
            HarvestEntity harvest = new HarvestEntity(); harvest.harvestId = harvestId; harvest.farmId = farmId;
            harvest.actorUserId = actorUserId; harvest.deviceId = deviceId; harvest.harvestedAtEpochMs = harvestedAtEpochMs;
            harvest.notes = notes; harvest.status = HarvestStatus.RECORDED;
            List<HarvestItemEntity> items = new ArrayList<>(); List<InventoryLedgerEntity> movements = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
                Integer count = entry.getValue(); if (count == null || count <= 0) throw new IllegalArgumentException("Harvest quantities must be greater than zero.");
                ProductEntity product = BusinessRules.product(db, entry.getKey());
                HarvestItemEntity item = new HarvestItemEntity(); item.harvestId = harvestId; item.productId = product.productId; item.quantityEggs = count;
                items.add(item);
                InventoryLedgerEntity movement = new InventoryLedgerEntity(); movement.farmId = farmId; movement.productId = product.productId;
                movement.quantityDeltaEggs = count; movement.eventType = InventoryEventType.HARVEST; movement.sourceType = "HARVEST";
                movement.sourceRecordId = harvestId; movement.actorUserId = actorUserId; movement.deviceId = deviceId; movement.occurredAtEpochMs = harvestedAtEpochMs;
                movements.add(movement);
            }
            db.harvestDao().insert(harvest); db.harvestItemDao().insertAll(items); db.inventoryLedgerDao().insertAll(movements);
            result[0] = harvest;
        });
        return result[0];
    }
}
