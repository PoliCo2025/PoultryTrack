package com.example.syncore.domain;

import androidx.annotation.Nullable;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.AuditLogEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.UserEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;

import java.util.Locale;
import java.util.function.LongSupplier;

/** Farm-scoped, immediate price changes with auditable effective-dated history. */
public final class PricingService {
    private final PoultryTrackDatabase db;
    private final LongSupplier clock;

    public PricingService(PoultryTrackDatabase db) { this(db, System::currentTimeMillis); }

    /** Clock injection keeps policy tests deterministic; production callers use the system clock. */
    public PricingService(PoultryTrackDatabase db, LongSupplier clock) {
        if (db == null || clock == null) throw new IllegalArgumentException("Database and clock are required.");
        this.db = db;
        this.clock = clock;
    }

    /** Saves a new version effective immediately. The reason is recorded in the audit log. */
    public PriceVersionEntity setPrice(String farmId, String productId, long amountMinorUnits,
                                       String currencyCode, String changedByUserId, String reason) {
        if (amountMinorUnits <= 0) throw new IllegalArgumentException("Price must be greater than zero.");
        if (currencyCode == null || currencyCode.trim().isEmpty()) throw new IllegalArgumentException("Currency is required.");
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("A reason is required for a price change.");
        final PriceVersionEntity[] created = new PriceVersionEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId);
            ProductEntity product = BusinessRules.product(db, productId);
            UserEntity actor = BusinessRules.user(db, farmId, changedByUserId);
            long effectiveAt = clock.getAsLong();
            if (effectiveAt < 0) throw new IllegalStateException("Application clock returned an invalid time.");
            PriceVersionEntity latest = db.priceVersionDao().getLatest(farmId, product.productId);
            if (latest != null && effectiveAt <= latest.effectiveFromEpochMs)
                throw new IllegalStateException("Price change rejected because the application clock is not later than the latest version.");
            if (latest != null && latest.effectiveToEpochMs == null) {
                latest.effectiveToEpochMs = effectiveAt;
                db.priceVersionDao().update(latest);
            }
            PriceVersionEntity price = new PriceVersionEntity();
            price.farmId = farmId;
            price.productId = product.productId;
            price.amountMinorUnits = amountMinorUnits;
            price.currencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
            price.effectiveFromEpochMs = effectiveAt;
            price.versionNumber = latest == null ? 1 : Math.addExact(latest.versionNumber, 1);
            price.status = PriceStatus.ACTIVE;
            price.changedByUserId = actor.userId;
            price.createdAtEpochMs = effectiveAt;
            db.priceVersionDao().insert(price);

            AuditLogEntity audit = new AuditLogEntity();
            audit.farmId = farmId;
            audit.actorUserId = actor.userId;
            audit.action = "PRICE_CHANGED";
            audit.affectedType = "PRICE_VERSION";
            audit.affectedRecordId = price.priceVersionId;
            audit.occurredAtEpochMs = effectiveAt;
            audit.detailsJson = "{\"reason\":" + jsonString(reason.trim())
                    + ",\"amountMinorUnits\":" + amountMinorUnits
                    + ",\"currencyCode\":" + jsonString(price.currencyCode)
                    + ",\"versionNumber\":" + price.versionNumber + "}";
            db.auditLogDao().insert(audit);
            created[0] = price;
        });
        return created[0];
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    @Nullable public PriceVersionEntity resolve(String farmId, String productId, long atEpochMs) {
        BusinessRules.farm(db, farmId);
        BusinessRules.product(db, productId);
        return db.priceVersionDao().findEffectiveAt(farmId, productId, atEpochMs);
    }
}
