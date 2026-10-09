package com.example.syncore.domain;

import androidx.annotation.Nullable;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.UserEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;

/** Farm-scoped, effective-dated price history. Monetary values are integer minor units. */
public final class PricingService {
    private final PoultryTrackDatabase db;
    public PricingService(PoultryTrackDatabase db) { this.db = db; }

    public PriceVersionEntity setPrice(String farmId, String productId, long amountMinorUnits,
                                       String currencyCode, long effectiveFromEpochMs,
                                       String changedByUserId) {
        if (amountMinorUnits <= 0) throw new IllegalArgumentException("Price must be greater than zero.");
        if (currencyCode == null || currencyCode.trim().isEmpty()) throw new IllegalArgumentException("Currency is required.");
        if (effectiveFromEpochMs < 0) throw new IllegalArgumentException("Effective time is invalid.");
        final PriceVersionEntity[] created = new PriceVersionEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId);
            ProductEntity product = BusinessRules.product(db, productId);
            UserEntity actor = BusinessRules.user(db, farmId, changedByUserId);
            PriceVersionEntity latest = db.priceVersionDao().getLatest(farmId, product.productId);
            if (latest != null && effectiveFromEpochMs <= latest.effectiveFromEpochMs)
                throw new IllegalArgumentException("New prices must have a later effective time than existing versions.");
            if (latest != null && latest.effectiveToEpochMs == null) {
                latest.effectiveToEpochMs = effectiveFromEpochMs;
                db.priceVersionDao().update(latest);
            }
            PriceVersionEntity price = new PriceVersionEntity();
            price.farmId = farmId;
            price.productId = productId;
            price.amountMinorUnits = amountMinorUnits;
            price.currencyCode = currencyCode.trim().toUpperCase(java.util.Locale.ROOT);
            price.effectiveFromEpochMs = effectiveFromEpochMs;
            price.versionNumber = latest == null ? 1 : Math.addExact(latest.versionNumber, 1);
            price.status = PriceStatus.ACTIVE;
            price.changedByUserId = actor.userId;
            price.createdAtEpochMs = effectiveFromEpochMs;
            db.priceVersionDao().insert(price);
            created[0] = price;
        });
        return created[0];
    }

    @Nullable public PriceVersionEntity resolve(String farmId, String productId, long atEpochMs) {
        BusinessRules.farm(db, farmId);
        BusinessRules.product(db, productId);
        return db.priceVersionDao().findEffectiveAt(farmId, productId, atEpochMs);
    }
}
