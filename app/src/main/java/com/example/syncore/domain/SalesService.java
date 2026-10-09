package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.DatabaseEnums.SaleStatus;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.ShiftStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Validates and posts cash sales atomically, including stock movements. */
public final class SalesService {
    public static final String DEFAULT_CURRENCY = "PHP";
    private final PoultryTrackDatabase db;
    public SalesService(PoultryTrackDatabase db) { this.db = db; }

    /** @param quantities egg counts keyed by catalog product id; saleId is the stable idempotency key. */
    public SaleEntity checkout(String saleId, String receiptNumber, String farmId, String actorUserId,
                               String shiftId, String deviceId, long soldAtEpochMs,
                               Map<String, Integer> quantities, long cashReceivedMinorUnits) {
        if (saleId == null || saleId.trim().isEmpty() || receiptNumber == null || receiptNumber.trim().isEmpty()) throw new IllegalArgumentException("Sale and receipt identifiers are required.");
        if (quantities == null || quantities.isEmpty()) throw new IllegalArgumentException("Cart cannot be empty.");
        if (cashReceivedMinorUnits < 0 || soldAtEpochMs < 0) throw new IllegalArgumentException("Payment and sale time cannot be negative.");
        final SaleEntity[] result = new SaleEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId); BusinessRules.user(db, farmId, actorUserId); BusinessRules.device(db, farmId, deviceId);
            if (db.saleDao().findById(saleId) != null || db.saleDao().findByReceiptNumber(receiptNumber) != null)
                throw new IllegalStateException("This sale request or receipt has already been processed.");
            ShiftEntity shift = db.shiftDao().findById(shiftId);
            if (shift == null || !farmId.equals(shift.farmId) || !actorUserId.equals(shift.userId) || shift.status != ShiftStatus.OPEN)
                throw new IllegalArgumentException("An open shift belonging to this farm and user is required.");
            if (soldAtEpochMs < shift.startedAtEpochMs) throw new IllegalArgumentException("Sale time precedes the active shift.");
            if (shift.deviceId != null && !shift.deviceId.equals(deviceId)) throw new IllegalArgumentException("Sale device does not match the active shift.");
            List<SaleItemEntity> items = new ArrayList<>();
            List<InventoryLedgerEntity> movements = new ArrayList<>();
            long total = 0;
            for (Map.Entry<String, Integer> entry : quantities.entrySet()) {
                String productId = entry.getKey(); Integer quantityValue = entry.getValue();
                if (quantityValue == null || quantityValue <= 0) throw new IllegalArgumentException("Sale quantities must be greater than zero.");
                ProductEntity product = BusinessRules.product(db, productId);
                PriceVersionEntity price = db.priceVersionDao().findEffectiveAt(farmId, productId, soldAtEpochMs);
                if (price == null || !farmId.equals(price.farmId) || !productId.equals(price.productId)) throw new IllegalStateException("No applicable price exists for " + product.name + ".");
                if (!DEFAULT_CURRENCY.equals(price.currencyCode)) throw new IllegalStateException("Price currency does not match the supported sale currency.");
                long stock = db.inventoryLedgerDao().getBalance(farmId, productId);
                if (stock < quantityValue) throw new IllegalStateException("Insufficient stock for " + product.name + ".");
                long line = Math.multiplyExact(price.amountMinorUnits, quantityValue.longValue());
                total = Math.addExact(total, line);
                SaleItemEntity item = new SaleItemEntity(); item.saleItemId = UUID.randomUUID().toString();
                item.saleId = saleId; item.productId = productId; item.priceVersionId = price.priceVersionId;
                item.quantityEggs = quantityValue; item.unitPriceMinorUnits = price.amountMinorUnits; item.lineTotalMinorUnits = line;
                items.add(item);
                InventoryLedgerEntity movement = movement(farmId, productId, actorUserId, deviceId,
                        -quantityValue, InventoryEventType.SALE, saleId, soldAtEpochMs);
                movements.add(movement);
            }
            if (cashReceivedMinorUnits < total) throw new IllegalArgumentException("Cash received is less than the sale total.");
            SaleEntity sale = new SaleEntity(); sale.saleId = saleId; sale.receiptNumber = receiptNumber;
            sale.farmId = farmId; sale.actorUserId = actorUserId; sale.shiftId = shiftId; sale.deviceId = deviceId;
            sale.soldAtEpochMs = soldAtEpochMs; sale.subtotalMinorUnits = total; sale.totalMinorUnits = total;
            sale.cashReceivedMinorUnits = cashReceivedMinorUnits; sale.changeMinorUnits = Math.subtractExact(cashReceivedMinorUnits, total);
            sale.status = SaleStatus.COMPLETED;
            PaymentEntity payment = new PaymentEntity(); payment.saleId = saleId; payment.paymentMethod = "CASH";
            payment.amountMinorUnits = total; payment.currencyCode = sale.currencyCode; payment.receivedAtEpochMs = soldAtEpochMs;
            db.saleDao().insert(sale); db.saleItemDao().insertAll(items); db.paymentDao().insert(payment);
            db.inventoryLedgerDao().insertAll(movements); result[0] = sale;
        });
        return result[0];
    }

    public SaleEntity checkout(String saleId, String receiptNumber, String farmId, String actorUserId,
                               String shiftId, String deviceId, long soldAtEpochMs,
                               Map<String, Integer> quantities, long cashReceivedMinorUnits, String currencyCode) {
        if (!DEFAULT_CURRENCY.equals(currencyCode)) throw new IllegalArgumentException("Only PHP cash sales are currently supported.");
        return checkout(saleId, receiptNumber, farmId, actorUserId, shiftId, deviceId, soldAtEpochMs, quantities, cashReceivedMinorUnits);
    }

    private static InventoryLedgerEntity movement(String farmId, String productId, String actorUserId,
            String deviceId, int delta, InventoryEventType type, String sourceId, long at) {
        InventoryLedgerEntity row = new InventoryLedgerEntity(); row.farmId = farmId; row.productId = productId;
        row.actorUserId = actorUserId; row.deviceId = deviceId; row.quantityDeltaEggs = delta;
        row.eventType = type; row.sourceType = type.name(); row.sourceRecordId = sourceId; row.occurredAtEpochMs = at;
        return row;
    }
}
