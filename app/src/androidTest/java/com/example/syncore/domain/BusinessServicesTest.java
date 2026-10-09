package com.example.syncore.domain;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
    private AtomicLong pricingClock;

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
        pricingClock = new AtomicLong(0);
        pricing = new PricingService(db, pricingClock::get); shifts = new ShiftService(db);
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
        pricingClock.set(400);
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 500, "PHP", user.userId, "MVP price change");
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

    @Test public void checkoutRejectsAPriceInAnotherCurrencyWithoutPostingAnything() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        pricingClock.set(20);
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 500, "USD", user.userId, "MVP price change");
        expectFailure(() -> sales.checkout("wrong-currency", "WRONG-CURRENCY", farm.farmId, user.userId,
                shift.shiftId, device.deviceId, 30, cart(ProductEntity.SMALL_ID, 1), 500));
        assertNull(db.saleDao().findById("wrong-currency"));
        assertTrue(db.paymentDao().getForSale("wrong-currency").isEmpty());
        assertEquals(10L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
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

    @Test public void concurrentSalesCannotSellMoreThanTheLastAvailableEgg() throws Exception {
        stock(ProductEntity.SMALL_ID, 1); prices(); ShiftEntity shift = openShift();
        int successes = runConcurrently(
                () -> sales.checkout("last-a", "LAST-A", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300),
                () -> sales.checkout("last-b", "LAST-B", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertEquals(1, successes);
        assertEquals(0L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(1, db.saleDao().getForFarm(farm.farmId).size());
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void concurrentRetriesOfSameSaleIdCreateOnlyOneSaleAndMovement() throws Exception {
        stock(ProductEntity.SMALL_ID, 5); prices(); ShiftEntity shift = openShift();
        int successes = runConcurrently(
                () -> sales.checkout("same-id", "SAME-A", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300),
                () -> sales.checkout("same-id", "SAME-B", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertEquals(1, successes);
        assertEquals(1, db.saleDao().getForFarm(farm.farmId).size());
        assertEquals(4L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void concurrentSaleAndNegativeAdjustmentCannotOverdrawSameProduct() throws Exception {
        stock(ProductEntity.SMALL_ID, 1); prices(); ShiftEntity shift = openShift();
        adjustments.request("race-adjustment", farm.farmId, user.userId, ProductEntity.SMALL_ID, device.deviceId, -1, "Count correction", 11);
        int successes = runConcurrently(
                () -> sales.checkout("race-sale", "RACE-SALE", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300),
                () -> adjustments.approve(farm.farmId, "race-adjustment", user.userId, 12));
        assertEquals(1, successes);
        assertEquals(0L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        int saleCount = db.saleDao().getForFarm(farm.farmId).size();
        boolean adjustmentApplied = db.adjustmentRequestDao().findById("race-adjustment").status == AdjustmentStatus.APPROVED;
        assertEquals(1, saleCount + (adjustmentApplied ? 1 : 0));
    }

    @Test public void concurrentCheckoutAndShiftCloseSerializeWithoutLosingSaleCash() throws Exception {
        stock(ProductEntity.SMALL_ID, 1); prices(); ShiftEntity shift = openShift();
        runConcurrently(
                () -> sales.checkout("closing-race-sale", "CLOSE-RACE", farm.farmId, user.userId,
                        shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300),
                () -> shifts.close(farm.farmId, shift.shiftId, user.userId, 5300, 20, "PHP"));
        ShiftEntity closed = db.shiftDao().findById(shift.shiftId);
        assertEquals("CLOSED", closed.status.name());
        if (db.saleDao().findById("closing-race-sale") != null) {
            assertEquals(5300L, closed.expectedCashMinorUnits.longValue());
            assertEquals(0L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        } else {
            assertEquals(5000L, closed.expectedCashMinorUnits.longValue());
            assertEquals(1L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        }
    }

    @Test public void saleRejectsCrossFarmActorOrShiftAndFarmInventoryIsIsolated() {
        stock(ProductEntity.SMALL_ID, 10); prices(); ShiftEntity shift = openShift();
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        expectFailure(() -> sales.checkout("cross", "RX", farm.farmId, otherUser.userId, shift.shiftId, device.deviceId, 300, cart(ProductEntity.SMALL_ID, 1), 100));
        assertEquals(0, db.inventoryLedgerDao().getBalance(other.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void farmBPriceShiftAndStockCannotBeUsedByFarmASale() {
        stock(ProductEntity.SMALL_ID, 10); prices(); openShift();
        FarmEntity farmB = new FarmEntity(); farmB.farmId = "farm-b"; farmB.name = "Farm B"; db.farmDao().insert(farmB);
        UserEntity userB = new UserEntity(); userB.userId = "user-b"; userB.farmId = farmB.farmId;
        userB.username = "b"; userB.usernameNormalized = "b"; userB.displayName = "User B"; db.userDao().insert(userB);
        ShiftEntity shiftB = shifts.start(farmB.farmId, userB.userId, null, 0, 10);
        harvests.record("farm-b-stock", farmB.farmId, userB.userId, null, 10, cart(ProductEntity.SMALL_ID, 50), "");
        assertNull(pricing.resolve(farmB.farmId, ProductEntity.SMALL_ID, 20));
        expectFailure(() -> sales.checkout("farm-cross-sale", "FARM-CROSS", farm.farmId, user.userId,
                shiftB.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        expectFailure(() -> sales.checkout("farm-no-price", "NO-PRICE", farmB.farmId, userB.userId,
                shiftB.shiftId, null, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertNull(db.saleDao().findById("farm-cross-sale"));
        assertNull(db.saleDao().findById("farm-no-price"));
        assertEquals(10L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(50L, db.inventoryLedgerDao().getBalance(farmB.farmId, ProductEntity.SMALL_ID));
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

    @Test public void failedSaleItemInsertRollsBackSaleHeader() {
        stock(ProductEntity.SMALL_ID, 5); prices(); ShiftEntity shift = openShift();
        abortBeforeInsert("sale_items", "force_sale_item_failure");
        expectFailure(() -> sales.checkout("item-failure", "ITEM-FAIL", farm.farmId, user.userId,
                shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertNull(db.saleDao().findById("item-failure"));
        assertTrue(db.saleItemDao().getForSale("item-failure").isEmpty());
        assertEquals(5L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void failedPaymentInsertRollsBackSaleAndItems() {
        stock(ProductEntity.SMALL_ID, 5); prices(); ShiftEntity shift = openShift();
        abortBeforeInsert("payments", "force_payment_failure");
        expectFailure(() -> sales.checkout("payment-failure", "PAY-FAIL", farm.farmId, user.userId,
                shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertNull(db.saleDao().findById("payment-failure"));
        assertTrue(db.saleItemDao().getForSale("payment-failure").isEmpty());
        assertTrue(db.paymentDao().getForSale("payment-failure").isEmpty());
        assertEquals(5L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
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

    @Test public void failedHarvestItemInsertRollsBackHarvestHeader() {
        abortBeforeInsert("harvest_items", "force_harvest_item_failure");
        expectFailure(() -> harvests.record("harvest-item-failure", farm.farmId, user.userId, device.deviceId,
                100, cart(ProductEntity.SMALL_ID, 5), ""));
        assertNull(db.harvestDao().findById("harvest-item-failure"));
        assertTrue(db.harvestItemDao().getForHarvest("harvest-item-failure").isEmpty());
        assertEquals(0L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
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

    @Test public void failedAdjustmentLedgerInsertKeepsRequestPendingAndStockUnchanged() {
        stock(ProductEntity.SMALL_ID, 10);
        adjustments.request("adjustment-failure", farm.farmId, user.userId, ProductEntity.SMALL_ID,
                device.deviceId, -2, "Correction", 10);
        InventoryLedgerEntity duplicate = new InventoryLedgerEntity(); duplicate.farmId = farm.farmId;
        duplicate.productId = ProductEntity.SMALL_ID; duplicate.quantityDeltaEggs = 0;
        duplicate.eventType = InventoryEventType.APPROVED_ADJUSTMENT; duplicate.sourceType = "ADJUSTMENT";
        duplicate.sourceRecordId = "adjustment-failure"; db.inventoryLedgerDao().insert(duplicate);
        expectFailure(() -> adjustments.approve(farm.farmId, "adjustment-failure", user.userId, 11));
        assertEquals(AdjustmentStatus.PENDING, db.adjustmentRequestDao().findById("adjustment-failure").status);
        assertEquals(10L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void pricingCreatesEffectiveVersionsAndPreservesHistory() {
        pricingClock.set(100);
        PriceVersionEntity p1 = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, "MVP price change");
        pricingClock.set(200);
        PriceVersionEntity p2 = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "MVP price change");
        assertEquals(100L, p1.effectiveFromEpochMs); assertEquals(Long.valueOf(200), db.priceVersionDao().findById(p1.priceVersionId).effectiveToEpochMs);
        assertEquals(p1.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 150).priceVersionId);
        assertEquals(p2.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 250).priceVersionId);
        assertEquals(2, db.priceVersionDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
        assertEquals(2, db.auditLogDao().getForFarm(farm.farmId).size());
        assertTrue(db.auditLogDao().getForRecord("PRICE_VERSION", p2.priceVersionId).get(0).detailsJson.contains("MVP price change"));
    }

    @Test public void priceChangeTakesEffectAtApplicationAssignedTimeAndRequiresReason() {
        pricingClock.set(1234);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, " "));
        PriceVersionEntity price = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, "Market price updated");
        assertEquals(1234L, price.effectiveFromEpochMs);
        assertEquals(price.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 1234).priceVersionId);
        assertEquals(price.priceVersionId, pricing.resolve(farm.farmId, ProductEntity.SMALL_ID, 1235).priceVersionId);
    }

    @Test public void pricingRejectsInvalidAmountNonchronologicalChangeAndCrossFarmUser() {
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 0, "PHP", user.userId, "MVP price change"));
        pricingClock.set(100);
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, "MVP price change");
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, -1, "PHP", user.userId, "MVP price change"));
        pricingClock.set(90);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "Attempted backdate"));
        pricingClock.set(100);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "Attempted same-time change"));
        assertEquals(1, db.priceVersionDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
        FarmEntity other = new FarmEntity(); other.farmId = "farm-b"; other.name = "Farm B"; db.farmDao().insert(other);
        UserEntity otherUser = new UserEntity(); otherUser.userId = "user-b"; otherUser.farmId = other.farmId; otherUser.username = "b"; otherUser.usernameNormalized = "b"; otherUser.displayName = "B"; db.userDao().insert(otherUser);
        pricingClock.set(200);
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", otherUser.userId, "MVP price change"));
    }

    @Test public void failedPriceInsertRollsBackClosingPreviousPriceVersion() {
        PriceVersionEntity original = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, "MVP price change");
        pricingClock.set(200);
        abortBeforeInsert("price_versions", "force_price_insert_failure");
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "MVP price change"));
        PriceVersionEntity persisted = db.priceVersionDao().findById(original.priceVersionId);
        assertNull(persisted.effectiveToEpochMs);
        assertEquals(1, db.priceVersionDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
    }

    @Test public void failedPriceAuditInsertRollsBackPriceVersionAndIntervalUpdate() {
        PriceVersionEntity original = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 600, "PHP", user.userId, "Initial price");
        pricingClock.set(200);
        abortBeforeInsert("audit_logs", "force_price_audit_failure");
        expectFailure(() -> pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "Audited change"));
        PriceVersionEntity persisted = db.priceVersionDao().findById(original.priceVersionId);
        assertNull(persisted.effectiveToEpochMs);
        assertEquals(1, db.priceVersionDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
        assertEquals(1, db.auditLogDao().getForFarm(farm.farmId).size());
    }

    @Test public void shiftsStartCloseReconcileAndEnforceSingleOpenSession() {
        ShiftEntity shift = shifts.start(farm.farmId, user.userId, device.deviceId, 5000, 10);
        expectFailure(() -> shifts.start(farm.farmId, user.userId, device.deviceId, 0, 11));
        stock(ProductEntity.SMALL_ID, 10); prices();
        sales.checkout("shift-sale", "SHIFT-R", farm.farmId, user.userId, shift.shiftId, device.deviceId, 20, cart(ProductEntity.SMALL_ID, 2), 2000);
        expectFailure(() -> shifts.close(farm.farmId, shift.shiftId, user.userId, 5600, 15, "PHP"));
        assertEquals("OPEN", db.shiftDao().findById(shift.shiftId).status.name());
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

    @Test public void checkoutMustMatchShiftStartTimeFarmAndBoundDevice() {
        stock(ProductEntity.SMALL_ID, 5); prices(); ShiftEntity shift = openShift();
        expectFailure(() -> sales.checkout("before-shift", "BEFORE", farm.farmId, user.userId, shift.shiftId, device.deviceId, 9, cart(ProductEntity.SMALL_ID, 1), 300));
        expectFailure(() -> sales.checkout("wrong-device", "DEVICE", farm.farmId, user.userId, shift.shiftId, null, 20, cart(ProductEntity.SMALL_ID, 1), 300));
        assertTrue(db.saleDao().getForFarm(farm.farmId).isEmpty());
        assertEquals(5L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
    }

    @Test public void shiftCloseRejectsUnsupportedCurrencyWithoutChangingOpenShift() {
        ShiftEntity shift = openShift();
        expectFailure(() -> shifts.close(farm.farmId, shift.shiftId, user.userId, 0, 30, "USD"));
        assertEquals("OPEN", db.shiftDao().findById(shift.shiftId).status.name());
        assertNull(db.shiftDao().findById(shift.shiftId).endedAtEpochMs);
    }

    @Test public void failedShiftCloseUpdateLeavesShiftOpenWithoutPartialReconciliation() {
        ShiftEntity shift = openShift(); abortBeforeUpdate("shifts", "force_shift_update_failure");
        expectFailure(() -> shifts.close(farm.farmId, shift.shiftId, user.userId, 200, 30, "PHP"));
        ShiftEntity persisted = db.shiftDao().findById(shift.shiftId);
        assertEquals("OPEN", persisted.status.name()); assertNull(persisted.endedAtEpochMs);
        assertNull(persisted.closingCashMinorUnits); assertNull(persisted.expectedCashMinorUnits);
    }

    private void prices() {
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 300, "PHP", user.userId, "MVP price change");
        pricing.setPrice(farm.farmId, ProductEntity.MEDIUM_ID, 1000, "PHP", user.userId, "MVP price change");
    }
    private void seedProduct(String id, String name, int order) {
        ProductEntity product = new ProductEntity(); product.productId = id; product.name = name; product.sortOrder = order;
        db.productDao().insert(product);
    }
    private void abortBeforeInsert(String table, String triggerName) {
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON " + table +
                " BEGIN SELECT RAISE(ABORT, 'forced test failure'); END");
    }
    private void abortBeforeUpdate(String table, String triggerName) {
        db.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON " + table +
                " BEGIN SELECT RAISE(ABORT, 'forced test failure'); END");
    }
    private static int runConcurrently(Runnable first, Runnable second) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch start = new CountDownLatch(1); CountDownLatch done = new CountDownLatch(2);
        AtomicInteger successes = new AtomicInteger();
        Runnable wrapFirst = () -> runAfterBarrier(first, ready, start, done, successes);
        Runnable wrapSecond = () -> runAfterBarrier(second, ready, start, done, successes);
        Thread a = new Thread(wrapFirst, "poultrytrack-race-a"); Thread b = new Thread(wrapSecond, "poultrytrack-race-b");
        a.start(); b.start();
        if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent calls did not reach the start barrier.");
        start.countDown();
        if (!done.await(15, TimeUnit.SECONDS)) throw new AssertionError("Concurrent service calls did not finish.");
        return successes.get();
    }
    private static void runAfterBarrier(Runnable action, CountDownLatch ready, CountDownLatch start,
                                        CountDownLatch done, AtomicInteger successes) {
        ready.countDown();
        try { if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Race barrier timed out."); action.run(); successes.incrementAndGet(); }
        catch (Throwable expectedFailure) { /* A rejected racing operation must not corrupt committed data. */ }
        finally { done.countDown(); }
    }
    private void stock(String productId, int count) { harvests.record("stock-" + productId + "-" + db.inventoryLedgerDao().getBalance(farm.farmId, productId), farm.farmId, user.userId, device.deviceId, 1, cart(productId, count), "opening stock fixture"); }
    private ShiftEntity openShift() { return shifts.start(farm.farmId, user.userId, device.deviceId, 5000, 10); }
    private static Map<String,Integer> cart(Object... values) { Map<String,Integer> map = new LinkedHashMap<>(); for (int i=0;i<values.length;i+=2) map.put((String)values[i], (Integer)values[i+1]); return map; }
    private static void expectFailure(Runnable action) { try { action.run(); fail("Expected operation to be rejected."); } catch (IllegalArgumentException | IllegalStateException expected) { } catch (android.database.sqlite.SQLiteConstraintException expected) { } }
}
