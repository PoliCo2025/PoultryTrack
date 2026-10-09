package com.example.syncore.domain;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.SaleStatus;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.UserEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.UserRole;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/** End-to-end Room-backed tests for Phase 2 business rules and transaction boundaries. */
@RunWith(AndroidJUnit4.class)
public class BusinessServicesTest {
    private PoultryTrackDatabase db;
    private FarmEntity farm;
    private UserEntity user;
    private DeviceEntity device;
    private SalesService sales;
    private HarvestService harvests;
    private InventoryAdjustmentService adjustments;
    private PricingService pricing;
    private ShiftService shifts;

    @Before public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PoultryTrackDatabase.class)
                .allowMainThreadQueries().build();
        seedProduct(ProductEntity.SMALL_ID, "Small", 0);
        seedProduct(ProductEntity.MEDIUM_ID, "Medium", 1);
        seedProduct(ProductEntity.LARGE_ID, "Large", 2);
        seedProduct(ProductEntity.EXTRA_LARGE_ID, "Extra Large", 3);
        farm = new FarmEntity(); farm.farmId = "farm-a"; farm.name = "Farm A"; db.farmDao().insert(farm);
        user = new UserEntity(); user.userId = "user-a"; user.farmId = farm.farmId; user.username = "a";
        user.usernameNormalized = "a"; user.displayName = "User A"; user.role = UserRole.ADMIN; db.userDao().insert(user);
        device = new DeviceEntity(); device.deviceId = "device-a"; device.farmId = farm.farmId; device.deviceName = "Device A"; db.deviceDao().insert(device);
        sales = new SalesService(db); harvests = new HarvestService(db); adjustments = new InventoryAdjustmentService(db);
        pricing = new PricingService(db); shifts = new ShiftService(db);
    }

    @After public void tearDown() { if (db != null) db.close(); }

    @Test public void successfulMultiProductSaleSnapshotsPriceAndPostsStockAtomically() {
        stock(ProductEntity.SMALL_ID, 20); stock(ProductEntity.MEDIUM_ID, 30); prices();
        ShiftEntity shift = openShift();
        Map<String,Integer> cart = cart(ProductEntity.SMALL_ID, 3, ProductEntity.MEDIUM_ID, 4);
        SaleEntity sale = sales.checkout("sale-1", "R-1", farm.farmId, user.userId, shift.shiftId,
                device.deviceId, 300, cart, 4900);
        assertEquals(4900L, sale.totalMinorUnits); assertEquals(0L, sale.changeMinorUnits.longValue());
        assertEquals(SaleStatus.COMPLETED, sale.status);
        assertEquals(17L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(26L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.MEDIUM_ID));
        assertEquals(2, db.saleItemDao().getForSale(sale.saleId).size());
        SaleItemEntity smallLine = null;
        for (SaleItemEntity line : db.saleItemDao().getForSale(sale.saleId)) {
            if (ProductEntity.SMALL_ID.equals(line.productId)) smallLine = line;
        }
        assertNotNull(smallLine);
        assertEquals(300L, smallLine.unitPriceMinorUnits);
        assertEquals(900L, smallLine.lineTotalMinorUnits);
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.MEDIUM_ID).size());
        assertEquals(1, db.paymentDao().getForSale(sale.saleId).size());
    }

    @Test public void rejectsEmptyCartAndBadQuantities() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        expectFailure(() -> sales.checkout("e1", "RE1", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, Collections.emptyMap(), 0));
        expectFailure(() -> sales.checkout("e2", "RE2", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 0), 0));
        expectFailure(() -> sales.checkout("e3", "RE3", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, -1), 0));
        assertTrue(db.saleDao().getForFarm(farm.farmId).isEmpty());
    }

    @Test public void rejectsInactiveProductMissingPriceAndInsufficientStock() {
        ShiftEntity shift = openShift();
        ProductEntity inactive = new ProductEntity(); inactive.productId = "egg-disabled"; inactive.name = "Disabled"; inactive.active = false; db.productDao().insert(inactive);
        expectFailure(() -> sales.checkout("i", "RI", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(inactive.productId, 1), 100));
        expectFailure(() -> sales.checkout("p", "RP", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 1), 100));
        stock(ProductEntity.SMALL_ID, 2); prices();
        expectFailure(() -> sales.checkout("s", "RS", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 3), 1000));
        assertNull(db.saleDao().findById("s"));
    }

    @Test public void rejectsInsufficientCashAndRollsBackAllSaleRows() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        expectFailure(() -> sales.checkout("cash", "RC", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 2), 199));
        assertNull(db.saleDao().findById("cash"));
        assertEquals(10L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void computesChangeAndDuplicateRequestIsRejected() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        SaleEntity sale = sales.checkout("repeat", "RR", farm.farmId, user.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 2), 700);
        assertEquals(100L, sale.changeMinorUnits.longValue());
        expectFailure(() -> sales.checkout("repeat", "RR2", farm.farmId, user.userId, shift.shiftId, device.deviceId, 301, cart(ProductEntity.SMALL_ID, 1), 100));
        assertEquals(1, db.saleDao().getForFarm(farm.farmId).size());
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void saleRejectsCrossFarmActorOrShiftAndFarmInventoryIsIsolated() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        expectFailure(() -> sales.checkout("cross", "RX", farm.farmId, otherUser.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 1), 100));
        assertEquals(0, db.inventoryLedgerDao().getBalance(other.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void saleRollsBackHeaderItemsAndPaymentIfLedgerUniquenessFails() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        InventoryLedgerEntity conflictingMove = new InventoryLedgerEntity();
        conflictingMove.farmId = farm.farmId; conflictingMove.productId = ProductEntity.SMALL_ID;
        conflictingMove.quantityDeltaEggs = -1; conflictingMove.eventType = InventoryEventType.SALE;
        conflictingMove.sourceType = "SALE"; conflictingMove.sourceRecordId = "rollback-sale";
        db.inventoryLedgerDao().insert(conflictingMove);
        expectFailure(() -> sales.checkout("rollback-sale", "ROLLBACK-R", farm.farmId, user.userId,
                shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 1), 300));
        assertNull(db.saleDao().findById("rollback-sale"));
        assertTrue(db.saleDao().findByReceiptNumber("ROLLBACK-R") == null);
        assertTrue(db.saleItemDao().getForSale("rollback-sale").isEmpty());
        assertTrue(db.paymentDao().getForSale("rollback-sale").isEmpty());
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void harvestPostsMultipleProductsAndRejectsEmptyNonpositiveOrDuplicate() {
        HarvestEntity h = harvests.record("h1", farm.farmId, user.userId, device.deviceId, 100,
                cart(ProductEntity.SMALL_ID, 10, ProductEntity.LARGE_ID, 7), "Morning");
        assertNotNull(h); assertEquals(10, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(7, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.LARGE_ID));
        expectFailure(() -> harvests.record("empty", farm.farmId, user.userId, device.deviceId, 101, Collections.emptyMap(), ""));
        expectFailure(() -> harvests.record("zero", farm.farmId, user.userId, device.deviceId, 101, cart(ProductEntity.SMALL_ID, 0), ""));
        expectFailure(() -> harvests.record("negative", farm.farmId, user.userId, device.deviceId, 101, cart(ProductEntity.SMALL_ID, -1), ""));
        expectFailure(() -> harvests.record("h1", farm.farmId, user.userId, device.deviceId, 101, cart(ProductEntity.SMALL_ID, 1), ""));
        assertEquals(1, db.harvestDao().getForFarm(farm.farmId).size());
    }

    @Test public void harvestRollsBackHeaderWhenLedgerMovementCannotBeInserted() {
        InventoryLedgerEntity collision = new InventoryLedgerEntity(); collision.farmId = farm.farmId; collision.productId = ProductEntity.SMALL_ID;
        collision.eventType = InventoryEventType.HARVEST; collision.sourceType = "HARVEST"; collision.sourceRecordId = "collision"; collision.quantityDeltaEggs = 1;
        db.inventoryLedgerDao().insert(collision);
        expectFailure(() -> harvests.record("collision", farm.farmId, user.userId, device.deviceId, 100, cart(ProductEntity.SMALL_ID, 5), ""));
        assertNull(db.harvestDao().findById("collision"));
        assertEquals(1, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void harvestRejectsCrossFarmUserAndDoesNotChangeOtherFarmInventory() {
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        expectFailure(() -> harvests.record("cross-h", farm.farmId, otherUser.userId, device.deviceId, 100, cart(ProductEntity.SMALL_ID, 10), ""));
        assertEquals(0, db.inventoryLedgerDao().getBalance(other.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void adjustmentRequestApprovalAndRejectionAreFarmScopedAndExactlyOnce() {
        stock(ProductEntity.MEDIUM_ID, 50);
        AdjustmentRequestEntity request = adjustments.request("adj1", farm.farmId, user.userId, ProductEntity.MEDIUM_ID, device.deviceId, -5, "Cracked", 100);
        assertEquals(AdjustmentStatus.PENDING, request.status);
        AdjustmentRequestEntity approved = adjustments.approve(farm.farmId, request.adjustmentRequestId, user.userId, 110);
        assertEquals(AdjustmentStatus.APPROVED, approved.status); assertEquals(user.userId, approved.reviewedByUserId);
        assertEquals(45, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.MEDIUM_ID));
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.MEDIUM_ID).size());
        expectFailure(() -> adjustments.approve(farm.farmId, "adj1", user.userId, 120));
        adjustments.request("adj2", farm.farmId, user.userId, ProductEntity.MEDIUM_ID, device.deviceId, 2, "Count correction", 130);
        assertEquals(AdjustmentStatus.REJECTED, adjustments.reject(farm.farmId, "adj2", user.userId, 140).status);
        assertEquals(45, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.MEDIUM_ID));
    }

    @Test public void adjustmentsRejectInvalidRequestsCrossFarmReviewAndNegativeStock() {
        expectFailure(() -> adjustments.request("bad", farm.farmId, user.userId, ProductEntity.SMALL_ID, device.deviceId, 0, "zero", 1));
        expectFailure(() -> adjustments.request("blank", farm.farmId, user.userId, ProductEntity.SMALL_ID, device.deviceId, 1, " ", 1));
        adjustments.request("adj", farm.farmId, user.userId, ProductEntity.SMALL_ID, device.deviceId, -2, "bad count", 1);
        expectFailure(() -> adjustments.approve(farm.farmId, "adj", user.userId, 2));
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        expectFailure(() -> adjustments.reject(farm.farmId, "adj", otherUser.userId, 2));
        assertEquals(AdjustmentStatus.PENDING, db.adjustmentRequestDao().findById("adj").status);
    }

    @Test public void pricingCreatesEffectiveVersionsAndPreservesHistory() {
        PriceVersionEntity p1 = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", 100, user.userId);
        PriceVersionEntity p2 = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", 200, user.userId);
        assertEquals(100L, p1.effectiveFromEpochMs); assertEquals(Long.valueOf(200), db.priceVersionDao().findById(p1.priceVersionId).effectiveToEpochMs);
        assertEquals(p1.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 150).priceVersionId);
        assertEquals(p2.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 250).priceVersionId);
        assertEquals(2, db.priceVersionDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void pricingRejectsInvalidAmountNonchronologicalChangeAndCrossFarmUser() {
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 0, "PHP", 100, user.userId));
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", 100, user.userId);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, -1, "PHP", 200, user.userId));
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", 90, user.userId));
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", 200, otherUser.userId));
    }

    @Test public void shiftsStartCloseReconcileAndEnforceSingleOpenSession() {
        ShiftEntity shift = shifts.start(farm.farmId, user.userId, device.deviceId, 5000, 10);
        expectFailure(() -> shifts.start(farm.farmId, user.userId, device.deviceId, 0, 11));
        stock(ProductEntity.SMALL_ID, 10); prices();
        sales.checkout("shift-sale", "SHIFT-R", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 2), 2000);
        ShiftEntity closed = shifts.close(farm.farmId, shift.shiftId, user.userId, 5600, 30, "PHP");
        assertEquals(5600L, closed.expectedCashMinorUnits.longValue()); assertEquals(0L, closed.differenceCashMinorUnits.longValue());
        expectFailure(() -> shifts.close(farm.farmId, shift.shiftId, user.userId, 5600, 31, "PHP"));
        assertNotNull(shifts.start(farm.farmId, user.userId, device.deviceId, 0, 40));
    }

    @Test public void shiftsRejectCrossFarmUserAndTimeInversion() {
        ShiftEntity shift = openShift();
        expectFailure(() -> shifts.close("other-farm", shift.shiftId, user.userId, 0, 300, "PHP"));
        expectFailure(() -> shifts.close(farm.farmId, shift.shiftId, user.userId, 0, 9, "PHP"));
        assertEquals("OPEN", db.shiftDao().findById(shift.shiftId).status.name());
    }

    private void prices() {
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 300, "PHP", 0, user.userId);
        pricing.setPrice(farm.farmId, ProductEntity.MEDIUM_ID, 1000, "PHP", 0, user.userId);
    }
    private void seedProduct(String id, String name, int order) {
        ProductEntity product = new ProductEntity(); product.productId = id; product.name = name; product.sortOrder = order;
        db.productDao().insert(product);
    }
    private void stock(String productId, int count) { harvests.record("stock-" + productId + "-" + db.inventoryLedgerDao().getBalance(farm.farmId, productId), farm.farmId, user.userId, device.deviceId, 1, cart(productId, count), "opening stock fixture"); }
    private ShiftEntity openShift() { return shifts.start(farm.farmId, user.userId, device.deviceId, 5000, 10); }
    private static Map<String,Integer> cart(Object... values) { Map<String,Integer> map = new LinkedHashMap<>(); for (int i=0;i<values.length;i+=2) map.put((String)values[i], (Integer)values[i+1]); return map; }
    private static void expectFailure(Runnable action) { try { action.run(); fail("Expected operation to be rejected."); } catch (IllegalArgumentException | IllegalStateException expected) { } catch (android.database.sqlite.SQLiteConstraintException expected) { } }
}
