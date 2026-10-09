package com.example.syncore.domain;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.DatabaseEnums.UserRole;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.UserEntity;
import com.example.syncore.data.repository.PosRepository;
import com.example.syncore.data.repository.PosRepository.PosCatalog;
import com.example.syncore.data.repository.PosRepository.PosProduct;
import com.example.syncore.data.repository.PosRepository.SaleReceipt;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PosIntegrationTest {
    private PoultryTrackDatabase db;
    private FarmEntity farm;
    private UserEntity user;
    private DeviceEntity device;
    private ShiftEntity shift;
    private PosRepository repository;
    private PosController controller;
    private AtomicLong clock;

    @Before public void setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PoultryTrackDatabase.class)
                .allowMainThreadQueries().build();
        seedProduct(ProductEntity.SMALL_ID, "Small", 0, true);
        seedProduct(ProductEntity.MEDIUM_ID, "Medium", 1, true);
        seedProduct(ProductEntity.LARGE_ID, "Large", 2, true);
        seedProduct(ProductEntity.EXTRA_LARGE_ID, "Extra Large", 3, true);
        repository = new PosRepository(db);
        controller = new PosController(db);
        clock = new AtomicLong(100);
    }

    @After public void tearDown() { if (db != null) db.close(); }

    @Test public void emptyDatabaseLoadsCatalogButDoesNotInventFarmPriceOrStock() {
        PosCatalog catalog = repository.loadCatalog(200);
        assertNull(catalog.context);
        assertEquals(4, catalog.products.size());
        assertNotNull(catalog.message);
        for (PosProduct product : catalog.products) {
            assertNull(product.price);
            assertEquals(0L, product.stockEggs);
            assertFalse(product.canSell());
        }
    }

    @Test public void catalogLoadsProductsCurrentPriceStockAndInactiveStateFromRoom() {
        createContext();
        new HarvestService(db).record("opening-harvest", farm.farmId, user.userId, device.deviceId, 110,
                Collections.singletonMap(ProductEntity.SMALL_ID, 12), "Opening count");
        new PricingService(db, clock::get).setPrice(farm.farmId, ProductEntity.SMALL_ID, 625, "PHP", user.userId, "Test fixture price");
        ProductEntity extraLarge = db.productDao().findById(ProductEntity.EXTRA_LARGE_ID);
        extraLarge.active = false;
        db.productDao().update(extraLarge);

        PosCatalog catalog = repository.loadCatalog(200);
        assertNotNull(catalog.context);
        assertEquals(farm.farmId, catalog.context.farmId);
        assertEquals(4, catalog.products.size());
        PosProduct small = find(catalog, ProductEntity.SMALL_ID);
        assertNotNull(small.price);
        assertEquals(625L, small.price.amountMinorUnits);
        assertEquals(12L, small.stockEggs);
        assertTrue(small.canSell());
        assertNull(find(catalog, ProductEntity.MEDIUM_ID).price);
        assertEquals(0L, find(catalog, ProductEntity.MEDIUM_ID).stockEggs);
        assertFalse(find(catalog, ProductEntity.EXTRA_LARGE_ID).product.active);
        assertFalse(find(catalog, ProductEntity.EXTRA_LARGE_ID).canSell());
    }

    @Test public void checkoutPersistsReceiptPaymentStockAndSnapshotAndRetryRecoversSameSale() {
        createContext();
        new HarvestService(db).record("opening-harvest", farm.farmId, user.userId, device.deviceId, 110,
                Collections.singletonMap(ProductEntity.SMALL_ID, 12), "Opening count");
        PricingService pricing = new PricingService(db, clock::get);
        PriceVersionEntity initial = pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 625, "PHP", user.userId, "Initial price");
        controller.loadCatalog(200);
        controller.cart().adjust(controller.currentCatalog(), ProductEntity.SMALL_ID, 1);
        controller.cart().adjust(controller.currentCatalog(), ProductEntity.SMALL_ID, 1);

        SaleReceipt first = controller.checkout("stable-sale-id", "PT-RECEIPT-1", 2000, 200);
        assertEquals(1250L, first.sale.totalMinorUnits);
        assertEquals(750L, first.sale.changeMinorUnits.longValue());
        assertEquals("Farm A", first.farmName);
        assertEquals("Test Seller", first.actorName);
        assertEquals(625L, first.items.get(0).saleItem.unitPriceMinorUnits);
        assertEquals(1, first.payments.size());
        assertEquals("CASH", first.payments.get(0).paymentMethod);
        assertEquals(10L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());

        clock.set(300);
        pricing.setPrice(farm.farmId, ProductEntity.SMALL_ID, 700, "PHP", user.userId, "Later price");
        SaleReceipt recovered = controller.checkout("stable-sale-id", "PT-RECEIPT-1", 2000, 301);
        assertEquals(first.sale.saleId, recovered.sale.saleId);
        assertEquals(625L, recovered.items.get(0).saleItem.unitPriceMinorUnits);
        assertEquals(initial.priceVersionId, recovered.items.get(0).saleItem.priceVersionId);
        assertEquals(1, db.saleDao().getForFarm(farm.farmId).size());
        assertEquals(2, db.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size());
        SaleReceipt reopened = repository.loadReceipt("stable-sale-id");
        assertNotNull(reopened);
        assertEquals("PT-RECEIPT-1", reopened.sale.receiptNumber);
    }

    @Test public void missingReceiptReturnsMissingInsteadOfSampleData() {
        assertNull(repository.loadReceipt("not-saved"));
    }

    @Test public void stockChangingAfterCatalogLoadRejectsCheckoutAndKeepsCart() {
        createContext();
        new HarvestService(db).record("opening-harvest", farm.farmId, user.userId, device.deviceId, 110,
                Collections.singletonMap(ProductEntity.SMALL_ID, 1), "Opening count");
        new PricingService(db, clock::get).setPrice(farm.farmId, ProductEntity.SMALL_ID, 500, "PHP", user.userId, "Initial price");
        controller.loadCatalog(200);
        controller.cart().adjust(controller.currentCatalog(), ProductEntity.SMALL_ID, 1);

        new SalesService(db).checkout("race-sale", "PT-RACE", farm.farmId, user.userId, shift.shiftId,
                device.deviceId, 210, Collections.singletonMap(ProductEntity.SMALL_ID, 1), 500);
        try {
            controller.checkout("pos-after-stock-change", "PT-POS-RETRY", 500, 220);
            fail("Expected the service to reject stock consumed since the POS catalog load.");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Insufficient stock"));
        }
        assertEquals(1, controller.cart().quantity(ProductEntity.SMALL_ID));
        assertNull(db.saleDao().findById("pos-after-stock-change"));
        assertEquals(0L, db.inventoryLedgerDao().getBalance(farm.farmId, ProductEntity.SMALL_ID));
    }

    private void createContext() {
        farm = new FarmEntity(); farm.farmId = "farm-pos"; farm.name = "Farm A"; db.farmDao().insert(farm);
        user = new UserEntity(); user.userId = "user-pos"; user.farmId = farm.farmId;
        user.username = "seller"; user.usernameNormalized = "seller"; user.displayName = "Test Seller"; user.role = UserRole.SALES_PERSONNEL;
        db.userDao().insert(user);
        device = new DeviceEntity(); device.deviceId = "device-pos"; device.farmId = farm.farmId; device.deviceName = "POS Device";
        db.deviceDao().insert(device);
        shift = new ShiftService(db).start(farm.farmId, user.userId, device.deviceId, 0, 100);
    }

    private void seedProduct(String id, String name, int order, boolean active) {
        ProductEntity product = new ProductEntity(); product.productId = id; product.name = name;
        product.sortOrder = order; product.active = active; db.productDao().insert(product);
    }

    private static PosProduct find(PosCatalog catalog, String productId) {
        for (PosProduct product : catalog.products) if (productId.equals(product.product.productId)) return product;
        throw new AssertionError("Catalog product missing: " + productId);
    }
}
