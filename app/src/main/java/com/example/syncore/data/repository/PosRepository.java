package com.example.syncore.data.repository;

import androidx.annotation.Nullable;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.InventoryBalance;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.UserEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read model for POS products and persisted receipts. All methods are synchronous and must run off the UI thread. */
public final class PosRepository {
    private final PoultryTrackDatabase db;

    public PosRepository(PoultryTrackDatabase db) { this.db = db; }

    public PosCatalog loadCatalog(long atEpochMs) {
        List<ProductEntity> products = db.productDao().getAllProducts();
        List<ShiftEntity> openShifts = db.shiftDao().getAllOpenShifts();
        if (openShifts.size() != 1) {
            String explanation = openShifts.isEmpty()
                    ? "POS is unavailable: this device has no existing open farm shift. Farm, staff, device, and shift setup are not connected in this phase."
                    : "POS is unavailable: more than one farm shift is open, so the active context is ambiguous.";
            return new PosCatalog(null, productsWithUnavailablePrices(products), explanation);
        }

        ShiftEntity shift = openShifts.get(0);
        FarmEntity farm = db.farmDao().findById(shift.farmId);
        UserEntity user = db.userDao().findById(shift.userId);
        DeviceEntity device = shift.deviceId == null ? null : db.deviceDao().findById(shift.deviceId);
        if (farm == null || !farm.active || user == null || !user.active || !shift.farmId.equals(user.farmId)
                || shift.deviceId == null || device == null || !shift.farmId.equals(device.farmId)) {
            String explanation = "POS is unavailable: the existing open shift is missing an active farm, staff member, or registered device.";
            return new PosCatalog(null, productsWithUnavailablePrices(products), explanation);
        }

        Map<String, Long> stockByProduct = new HashMap<>();
        for (InventoryBalance balance : db.inventoryLedgerDao().getBalances(farm.farmId))
            stockByProduct.put(balance.productId, balance.quantityOnHand);
        List<PosProduct> posProducts = new ArrayList<>();
        for (ProductEntity product : products) {
            PriceVersionEntity price = product.active
                    ? db.priceVersionDao().findEffectiveAt(farm.farmId, product.productId, atEpochMs) : null;
            posProducts.add(new PosProduct(product, price, stockByProduct.containsKey(product.productId)
                    ? stockByProduct.get(product.productId) : db.inventoryLedgerDao().getBalance(farm.farmId, product.productId)));
        }
        String notice = posProducts.isEmpty() ? "No products are available in the catalog." : null;
        return new PosCatalog(new PosContext(farm.farmId, farm.name, user.userId, user.displayName,
                shift.shiftId, device.deviceId, shift.startedAtEpochMs), posProducts, notice);
    }

    @Nullable public SaleReceipt loadReceipt(String saleId) {
        if (saleId == null || saleId.trim().isEmpty()) return null;
        SaleEntity sale = db.saleDao().findById(saleId);
        if (sale == null) return null;
        List<SaleItemEntity> storedItems = db.saleItemDao().getForSale(saleId);
        List<ReceiptItem> items = new ArrayList<>();
        FarmEntity farm = db.farmDao().findById(sale.farmId);
        UserEntity actor = db.userDao().findById(sale.actorUserId);
        for (SaleItemEntity item : storedItems) {
            ProductEntity product = db.productDao().findById(item.productId);
            items.add(new ReceiptItem(item, product == null ? "Unknown egg size" : product.name));
        }
        return new SaleReceipt(sale, farm == null ? "Farm record unavailable" : farm.name,
                actor == null ? "Staff record unavailable" : actor.displayName,
                items, db.paymentDao().getForSale(saleId));
    }

    private static List<PosProduct> productsWithUnavailablePrices(List<ProductEntity> products) {
        List<PosProduct> rows = new ArrayList<>();
        for (ProductEntity product : products) rows.add(new PosProduct(product, null, 0));
        return rows;
    }

    public static final class PosContext {
        public final String farmId, farmName, userId, userName, shiftId, deviceId;
        public final long shiftStartedAtEpochMs;
        public PosContext(String farmId, String farmName, String userId, String userName, String shiftId,
                          String deviceId, long shiftStartedAtEpochMs) {
            this.farmId = farmId; this.farmName = farmName; this.userId = userId; this.userName = userName;
            this.shiftId = shiftId; this.deviceId = deviceId; this.shiftStartedAtEpochMs = shiftStartedAtEpochMs;
        }
    }

    public static final class PosProduct {
        public final ProductEntity product;
        @Nullable public final PriceVersionEntity price;
        public final long stockEggs;
        public PosProduct(ProductEntity product, @Nullable PriceVersionEntity price, long stockEggs) {
            this.product = product; this.price = price; this.stockEggs = stockEggs;
        }
        public boolean canSell() {
            return product.active && price != null && "PHP".equals(price.currencyCode) && stockEggs > 0;
        }
    }

    public static final class PosCatalog {
        @Nullable public final PosContext context;
        public final List<PosProduct> products;
        @Nullable public final String message;
        public PosCatalog(@Nullable PosContext context, List<PosProduct> products, @Nullable String message) {
            this.context = context; this.products = products; this.message = message;
        }
    }

    public static final class ReceiptItem {
        public final SaleItemEntity saleItem;
        public final String productName;
        ReceiptItem(SaleItemEntity saleItem, String productName) { this.saleItem = saleItem; this.productName = productName; }
    }

    public static final class SaleReceipt {
        public final SaleEntity sale;
        public final String farmName, actorName;
        public final List<ReceiptItem> items;
        public final List<PaymentEntity> payments;
        SaleReceipt(SaleEntity sale, String farmName, String actorName, List<ReceiptItem> items, List<PaymentEntity> payments) {
            this.sale = sale; this.farmName = farmName; this.actorName = actorName;
            this.items = items; this.payments = payments;
        }
    }
}
