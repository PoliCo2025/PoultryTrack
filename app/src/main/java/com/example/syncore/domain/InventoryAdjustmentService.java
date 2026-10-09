package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.ProductEntity;

/** Adjustment requests are pending until an explicit reviewer transition; approval posts stock exactly once. */
public final class InventoryAdjustmentService {
    private final PoultryTrackDatabase db;
    public InventoryAdjustmentService(PoultryTrackDatabase db) { this.db = db; }

    public AdjustmentRequestEntity request(String requestId, String farmId, String requestedByUserId,
            String productId, String deviceId, int deltaEggs, String reason, long requestedAtEpochMs) {
        if (requestId == null || requestId.trim().isEmpty() || deltaEggs == 0) throw new IllegalArgumentException("Request id and a non-zero adjustment are required.");
        if (reason == null || reason.trim().isEmpty() || requestedAtEpochMs < 0) throw new IllegalArgumentException("A reason and valid request time are required.");
        final AdjustmentRequestEntity[] result = new AdjustmentRequestEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId); BusinessRules.user(db, farmId, requestedByUserId);
            BusinessRules.device(db, farmId, deviceId); ProductEntity product = BusinessRules.product(db, productId);
            AdjustmentRequestEntity request = new AdjustmentRequestEntity(); request.adjustmentRequestId = requestId;
            request.farmId = farmId; request.requestedByUserId = requestedByUserId; request.productId = product.productId;
            request.deviceId = deviceId; request.quantityDeltaEggs = deltaEggs; request.reason = reason.trim();
            request.status = AdjustmentStatus.PENDING; request.requestedAtEpochMs = requestedAtEpochMs;
            db.adjustmentRequestDao().insert(request); result[0] = request;
        });
        return result[0];
    }

    public AdjustmentRequestEntity approve(String farmId, String requestId, String reviewerUserId, long reviewedAtEpochMs) {
        return review(farmId, requestId, reviewerUserId, reviewedAtEpochMs, true);
    }
    public AdjustmentRequestEntity reject(String farmId, String requestId, String reviewerUserId, long reviewedAtEpochMs) {
        return review(farmId, requestId, reviewerUserId, reviewedAtEpochMs, false);
    }

    private AdjustmentRequestEntity review(String farmId, String requestId, String reviewerUserId, long at, boolean approve) {
        if (at < 0) throw new IllegalArgumentException("Review time cannot be negative.");
        final AdjustmentRequestEntity[] result = new AdjustmentRequestEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId); BusinessRules.user(db, farmId, reviewerUserId);
            AdjustmentRequestEntity request = db.adjustmentRequestDao().findById(requestId);
            if (request == null || !farmId.equals(request.farmId)) throw new IllegalArgumentException("Adjustment request does not belong to this farm.");
            if (request.status != AdjustmentStatus.PENDING) throw new IllegalStateException("Only pending requests can be reviewed.");
            if (at < request.requestedAtEpochMs) throw new IllegalArgumentException("Review time precedes request time.");
            if (approve && request.quantityDeltaEggs < 0) {
                long current = db.inventoryLedgerDao().getBalance(farmId, request.productId);
                if (current < -(long) request.quantityDeltaEggs) throw new IllegalStateException("Adjustment would make inventory negative.");
            }
            request.status = approve ? AdjustmentStatus.APPROVED : AdjustmentStatus.REJECTED;
            request.reviewedByUserId = reviewerUserId; request.reviewedAtEpochMs = at; request.updatedAtEpochMs = at;
            db.adjustmentRequestDao().update(request);
            if (approve) {
                InventoryLedgerEntity movement = new InventoryLedgerEntity(); movement.farmId = farmId; movement.productId = request.productId;
                movement.quantityDeltaEggs = request.quantityDeltaEggs; movement.eventType = InventoryEventType.APPROVED_ADJUSTMENT;
                movement.sourceType = "ADJUSTMENT"; movement.sourceRecordId = requestId; movement.actorUserId = reviewerUserId;
                movement.deviceId = request.deviceId; movement.occurredAtEpochMs = at;
                db.inventoryLedgerDao().insert(movement);
            }
            result[0] = request;
        });
        return result[0];
    }
}
