package com.example.syncore.data.local;

import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.AuditLogEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.AdjustmentStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.InventoryEventType;
import com.example.syncore.data.local.entity.DatabaseEnums.OutboxOperation;
import com.example.syncore.data.local.entity.DatabaseEnums.SaleStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.ShiftStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.SyncStatus;
import com.example.syncore.data.local.entity.DatabaseEnums.UserRole;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.HarvestItemEntity;
import com.example.syncore.data.local.entity.InventoryBalance;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.SyncOutboxEntity;
import com.example.syncore.data.local.entity.UserEntity;
import com.example.syncore.data.repository.HarvestRepository;
import com.example.syncore.data.repository.SaleRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Persistence and referential-integrity tests only; no business calculations are tested here. */
@RunWith(AndroidJUnit4.class)
public class PoultryTrackDatabaseTest {
    @Rule public final MigrationTestHelper migrationTestHelper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), PoultryTrackDatabase.class);

    private PoultryTrackDatabase database;
    private FarmEntity farm;
    private DeviceEntity device;
    private UserEntity user;

    @Before public void setUp() {
        database = Room.inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext(), PoultryTrackDatabase.class)
                .allowMainThreadQueries()
                .addCallback(PoultryTrackDatabase.initialCallback())
                .build();

        farm = new FarmEntity();
        farm.farmId = "farm-test";
        farm.name = "Test Farm";
        database.farmDao().insert(farm);

        device = new DeviceEntity();
        device.deviceId = "device-test";
        device.farmId = farm.farmId;
        device.deviceName = "Test device";
        database.deviceDao().insert(device);

        user = new UserEntity();
        user.userId = "user-test";
        user.farmId = farm.farmId;
        user.username = "Maria.Santos";
        user.usernameNormalized = "maria.santos";
        user.displayName = "Maria Santos";
        user.role = UserRole.SALES_PERSONNEL;
        database.userDao().insert(user);
    }

    @After public void tearDown() {
        if (database != null) database.close();
    }

    @Test public void initialDatabaseContainsOnlyTheFourReferenceEggSizes() {
        List<ProductEntity> products = database.productDao().getActiveProducts();
        assertEquals(4, products.size());
        assertEquals("Small", products.get(0).name);
        assertEquals("Medium", products.get(1).name);
        assertEquals("Large", products.get(2).name);
        assertEquals("Extra Large", products.get(3).name);
    }

    @Test public void userCanBeInsertedRetrievedAndLookedUpByNormalizedUsername() {
        UserEntity found = database.userDao().findById(user.userId);
        assertNotNull(found);
        assertEquals(UserRole.SALES_PERSONNEL, found.role);
        assertEquals(user.userId, database.userDao().findByFarmAndNormalizedUsername(
                farm.farmId, "maria.santos").userId);
    }

    @Test public void usernamesAreUniqueWithinEachFarm() {
        FarmEntity otherFarm = new FarmEntity();
        otherFarm.farmId = "farm-second";
        otherFarm.name = "Second Farm";
        database.farmDao().insert(otherFarm);

        UserEntity sameNameAtOtherFarm = new UserEntity();
        sameNameAtOtherFarm.farmId = otherFarm.farmId;
        sameNameAtOtherFarm.username = "maria.santos";
        sameNameAtOtherFarm.usernameNormalized = "maria.santos";
        sameNameAtOtherFarm.displayName = "Maria Santos at Second Farm";
        database.userDao().insert(sameNameAtOtherFarm);

        assertEquals(user.userId, database.userDao().findByFarmAndNormalizedUsername(
                farm.farmId, "maria.santos").userId);
        assertEquals(sameNameAtOtherFarm.userId, database.userDao().findByFarmAndNormalizedUsername(
                otherFarm.farmId, "maria.santos").userId);

        UserEntity duplicateAtSameFarm = new UserEntity();
        duplicateAtSameFarm.farmId = farm.farmId;
        duplicateAtSameFarm.username = "MARIA.SANTOS";
        duplicateAtSameFarm.usernameNormalized = "maria.santos";
        duplicateAtSameFarm.displayName = "Duplicate User";
        try {
            database.userDao().insert(duplicateAtSameFarm);
            fail("Duplicate normalized usernames within one farm must be rejected.");
        } catch (SQLiteConstraintException expected) {
            // Expected farm-scoped unique-index check.
        }
    }

    @Test public void foreignKeysRejectMissingFarmAndCrossFarmActor() {
        UserEntity invalidUser = new UserEntity();
        invalidUser.farmId = "missing-farm";
        invalidUser.username = "nobody";
        invalidUser.usernameNormalized = "nobody";
        invalidUser.displayName = "Nobody";
        try {
            database.userDao().insert(invalidUser);
            fail("A user with no farm must violate its foreign key.");
        } catch (SQLiteConstraintException expected) {
            // Expected database-level referential-integrity check.
        }

        FarmEntity otherFarm = new FarmEntity();
        otherFarm.farmId = "other-farm";
        otherFarm.name = "Other Farm";
        database.farmDao().insert(otherFarm);
        SaleEntity wrongFarmSale = new SaleEntity();
        wrongFarmSale.saleId = "cross-farm-sale";
        wrongFarmSale.receiptNumber = "R-CROSS-FARM";
        wrongFarmSale.farmId = otherFarm.farmId;
        wrongFarmSale.actorUserId = user.userId;
        try {
            database.saleDao().insert(wrongFarmSale);
            fail("A sale must not reference an actor from another farm.");
        } catch (SQLiteConstraintException expected) {
            // Expected composite foreign-key check.
        }
    }

    @Test public void pricesRetainHistoryAndEffectiveLookupSelectsTheMatchingVersion() {
        PriceVersionEntity first = price("price-v1", 1, 600, 100L, 200L);
        PriceVersionEntity second = price("price-v2", 2, 700, 200L, null);
        database.priceVersionDao().insert(first);
        database.priceVersionDao().insert(second);

        assertEquals("price-v1", database.priceVersionDao().findEffectiveAt(
                farm.farmId, ProductEntity.MEDIUM_ID, 150).priceVersionId);
        assertEquals("price-v2", database.priceVersionDao().findEffectiveAt(
                farm.farmId, ProductEntity.MEDIUM_ID, 250).priceVersionId);
        assertEquals(2, database.priceVersionDao().getHistory(farm.farmId, ProductEntity.MEDIUM_ID).size());
    }

    @Test public void saleItemsKeepPriceSnapshotsAndPaymentsPersistWithSale() {
        PriceVersionEntity price = price("price-sale", 1, 700, 100L, null);
        database.priceVersionDao().insert(price);
        ShiftEntity shift = newShift("shift-sale");
        database.shiftDao().insert(shift);

        SaleEntity sale = newSale("sale-test", "PT-0001", shift.shiftId);
        sale.status = SaleStatus.COMPLETED;
        sale.subtotalMinorUnits = 1400;
        sale.totalMinorUnits = 1400;
        sale.cashReceivedMinorUnits = 2000L;
        sale.changeMinorUnits = 600L;

        SaleItemEntity item = new SaleItemEntity();
        item.saleId = sale.saleId;
        item.productId = ProductEntity.MEDIUM_ID;
        item.priceVersionId = price.priceVersionId;
        item.quantityEggs = 2;
        item.unitPriceMinorUnits = 700;
        item.lineTotalMinorUnits = 1400;

        PaymentEntity payment = new PaymentEntity();
        payment.saleId = sale.saleId;
        payment.paymentMethod = "CASH";
        payment.amountMinorUnits = 1400;
        payment.receivedAtEpochMs = 300;

        new SaleRepository(database).save(sale, Collections.singletonList(item), Collections.singletonList(payment));

        SaleItemEntity storedItem = database.saleItemDao().getForSale(sale.saleId).get(0);
        assertEquals(700L, storedItem.unitPriceMinorUnits);
        assertEquals("price-sale", storedItem.priceVersionId);
        assertEquals(1, database.paymentDao().getForSale(sale.saleId).size());
        assertEquals("sale-test", database.saleDao().findByReceiptNumber("PT-0001").saleId);
    }

    @Test public void receiptNumbersAreUnique() {
        SaleEntity first = newSale("sale-first", "PT-DUPLICATE", null);
        database.saleDao().insert(first);

        SaleEntity duplicate = newSale("sale-second", "PT-DUPLICATE", null);
        try {
            database.saleDao().insert(duplicate);
            fail("Duplicate receipt numbers must be rejected by the database.");
        } catch (SQLiteConstraintException expected) {
            // Expected unique-index check.
        }
        assertEquals("sale-first", database.saleDao().findByReceiptNumber("PT-DUPLICATE").saleId);
    }

    @Test public void saleAggregateRollsBackWhenAChildViolatesAConstraint() {
        SaleEntity sale = newSale("sale-rollback", "PT-ROLLBACK", null);
        SaleItemEntity badItem = new SaleItemEntity();
        badItem.saleId = sale.saleId;
        badItem.productId = "missing-product";
        badItem.quantityEggs = 1;

        try {
            new SaleRepository(database).save(sale, Collections.singletonList(badItem), Collections.emptyList());
            fail("The invalid child record should fail its product foreign key.");
        } catch (SQLiteConstraintException expected) {
            // The surrounding Room transaction must also roll back the inserted sale header.
        }
        assertNull(database.saleDao().findById(sale.saleId));
    }

    @Test public void harvestAndItemsPersistAtomically() {
        HarvestEntity harvest = new HarvestEntity();
        harvest.harvestId = "harvest-test";
        harvest.farmId = farm.farmId;
        harvest.actorUserId = user.userId;
        harvest.deviceId = device.deviceId;
        harvest.harvestedAtEpochMs = 500;

        HarvestItemEntity item = new HarvestItemEntity();
        item.harvestId = harvest.harvestId;
        item.productId = ProductEntity.SMALL_ID;
        item.quantityEggs = 60;
        new HarvestRepository(database).save(harvest, Collections.singletonList(item));

        assertEquals(60, database.harvestItemDao().getForHarvest(harvest.harvestId).get(0).quantityEggs);
        assertEquals("harvest-test", database.harvestDao().findById(harvest.harvestId).harvestId);
    }

    @Test public void ledgerPersistsHistoryAndDerivesCurrentBalance() {
        InventoryLedgerEntity harvest = ledger("ledger-harvest", InventoryEventType.HARVEST,
                "HARVEST_ITEM", "harvest-item-1", 60);
        InventoryLedgerEntity sale = ledger("ledger-sale", InventoryEventType.SALE,
                "SALE_ITEM", "sale-item-1", -12);
        database.inventoryLedgerDao().insert(harvest);
        database.inventoryLedgerDao().insert(sale);

        InventoryBalance balance = null;
        for (InventoryBalance entry : database.inventoryLedgerDao().getBalances(farm.farmId)) {
            if (ProductEntity.MEDIUM_ID.equals(entry.productId)) balance = entry;
        }
        assertNotNull(balance);
        assertEquals(48L, balance.quantityOnHand);
        assertEquals(2, database.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.MEDIUM_ID).size());
    }

    @Test public void ledgerAllowsDifferentProductsForOneSourceButRejectsDuplicateProduct() {
        InventoryLedgerEntity small = ledger("ledger-sale-small", InventoryEventType.SALE,
                "SALE", "sale-multi-size", -10);
        small.productId = ProductEntity.SMALL_ID;
        InventoryLedgerEntity medium = ledger("ledger-sale-medium", InventoryEventType.SALE,
                "SALE", "sale-multi-size", -20);
        medium.productId = ProductEntity.MEDIUM_ID;
        InventoryLedgerEntity large = ledger("ledger-sale-large", InventoryEventType.SALE,
                "SALE", "sale-multi-size", -5);
        large.productId = ProductEntity.LARGE_ID;

        database.inventoryLedgerDao().insertAll(java.util.Arrays.asList(small, medium, large));
        assertEquals(3, database.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.SMALL_ID).size()
                + database.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.MEDIUM_ID).size()
                + database.inventoryLedgerDao().getHistory(farm.farmId, ProductEntity.LARGE_ID).size());

        InventoryLedgerEntity duplicateSmall = ledger("ledger-sale-small-duplicate", InventoryEventType.SALE,
                "SALE", "sale-multi-size", -10);
        duplicateSmall.productId = ProductEntity.SMALL_ID;
        try {
            database.inventoryLedgerDao().insert(duplicateSmall);
            fail("A source transaction cannot post the same product twice.");
        } catch (SQLiteConstraintException expected) {
            // Expected event/source/product unique-index check.
        }
    }

    @Test public void migration1To2PreservesRowsAndAppliesUpdatedLedgerConstraint() throws IOException {
        String databaseName = "poultrytrack-phase-1-5-migration";
        SupportSQLiteDatabase versionOne = migrationTestHelper.createDatabase(databaseName, 1);
        versionOne.execSQL("INSERT INTO farms (farm_id, name, location, is_active, created_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('farm-migration', 'Migration Farm', NULL, 1, 100, 100)");
        versionOne.execSQL("INSERT INTO products (product_id, name, unit, sort_order, is_active, created_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('egg-small', 'Small', 'egg', 0, 1, 100, 100)");
        versionOne.execSQL("INSERT INTO products (product_id, name, unit, sort_order, is_active, created_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('egg-medium', 'Medium', 'egg', 1, 1, 100, 100)");
        versionOne.execSQL("INSERT INTO inventory_ledger (ledger_entry_id, farm_id, product_id, quantity_delta_eggs, " +
                "event_type, source_type, source_record_id, actor_user_id, device_id, occurred_at_epoch_ms) " +
                "VALUES ('ledger-v1', 'farm-migration', 'egg-small', -10, 'SALE', 'SALE', 'sale-v1', NULL, NULL, 200)");
        versionOne.close();

        SupportSQLiteDatabase migratedSchema = migrationTestHelper.runMigrationsAndValidate(
                databaseName, 2, true, PoultryTrackDatabase.MIGRATION_1_2);
        migratedSchema.close();

        PoultryTrackDatabase migrated = Room.databaseBuilder(
                        ApplicationProvider.getApplicationContext(), PoultryTrackDatabase.class, databaseName)
                .allowMainThreadQueries()
                .addMigrations(PoultryTrackDatabase.MIGRATION_1_2, PoultryTrackDatabase.MIGRATION_2_3)
                .build();
        try {
            assertEquals(1, migrated.inventoryLedgerDao().getHistory(
                    "farm-migration", ProductEntity.SMALL_ID).size());

            InventoryLedgerEntity secondProduct = new InventoryLedgerEntity();
            secondProduct.ledgerEntryId = "ledger-v2-medium";
            secondProduct.farmId = "farm-migration";
            secondProduct.productId = ProductEntity.MEDIUM_ID;
            secondProduct.eventType = InventoryEventType.SALE;
            secondProduct.sourceType = "SALE";
            secondProduct.sourceRecordId = "sale-v1";
            secondProduct.quantityDeltaEggs = -20;
            secondProduct.occurredAtEpochMs = 300;
            migrated.inventoryLedgerDao().insert(secondProduct);
            assertEquals(1, migrated.inventoryLedgerDao().getHistory(
                    "farm-migration", ProductEntity.MEDIUM_ID).size());

            InventoryLedgerEntity duplicateProduct = new InventoryLedgerEntity();
            duplicateProduct.ledgerEntryId = "ledger-v2-small-duplicate";
            duplicateProduct.farmId = "farm-migration";
            duplicateProduct.productId = ProductEntity.SMALL_ID;
            duplicateProduct.eventType = InventoryEventType.SALE;
            duplicateProduct.sourceType = "SALE";
            duplicateProduct.sourceRecordId = "sale-v1";
            duplicateProduct.quantityDeltaEggs = -1;
            duplicateProduct.occurredAtEpochMs = 400;
            try {
                migrated.inventoryLedgerDao().insert(duplicateProduct);
                fail("Migration must preserve duplicate protection for the same source product.");
            } catch (SQLiteConstraintException expected) {
                // Expected migrated unique-index check.
            }

            InventoryLedgerEntity invalidFarm = new InventoryLedgerEntity();
            invalidFarm.ledgerEntryId = "ledger-invalid-farm";
            invalidFarm.farmId = "missing-farm";
            invalidFarm.productId = ProductEntity.SMALL_ID;
            invalidFarm.eventType = InventoryEventType.SALE;
            invalidFarm.sourceType = "SALE";
            invalidFarm.sourceRecordId = "sale-invalid-farm";
            try {
                migrated.inventoryLedgerDao().insert(invalidFarm);
                fail("The migration must retain the ledger farm foreign key.");
            } catch (SQLiteConstraintException expected) {
                // Expected foreign-key check after migration.
            }
        } finally {
            migrated.close();
        }
    }

    @Test public void migration2To3AddsOneOpenShiftConstraintWithoutRemovingRows() throws IOException {
        String databaseName = "poultrytrack-phase-2-shift-migration";
        SupportSQLiteDatabase versionTwo = migrationTestHelper.createDatabase(databaseName, 2);
        versionTwo.execSQL("INSERT INTO farms (farm_id, name, location, is_active, created_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('farm-shift-migration', 'Shift Farm', NULL, 1, 100, 100)");
        versionTwo.execSQL("INSERT INTO users (user_id, farm_id, username, username_normalized, display_name, role, is_active, created_at_epoch_ms, updated_at_epoch_ms) " +
                "VALUES ('user-shift-migration', 'farm-shift-migration', 'staff', 'staff', 'Staff', 'SALES_PERSONNEL', 1, 100, 100)");
        versionTwo.execSQL("INSERT INTO shifts (shift_id, farm_id, user_id, device_id, started_at_epoch_ms, ended_at_epoch_ms, opening_cash_minor_units, closing_cash_minor_units, expected_cash_minor_units, difference_cash_minor_units, status) " +
                "VALUES ('shift-migration-open', 'farm-shift-migration', 'user-shift-migration', NULL, 200, NULL, 0, NULL, NULL, NULL, 'OPEN')");
        versionTwo.close();

        SupportSQLiteDatabase migratedSchema = migrationTestHelper.runMigrationsAndValidate(
                databaseName, 3, true, PoultryTrackDatabase.MIGRATION_2_3);
        try {
            try {
                migratedSchema.execSQL("INSERT INTO shifts (shift_id, farm_id, user_id, device_id, started_at_epoch_ms, ended_at_epoch_ms, opening_cash_minor_units, closing_cash_minor_units, expected_cash_minor_units, difference_cash_minor_units, status, open_user_id) " +
                        "VALUES ('shift-migration-open-duplicate', 'farm-shift-migration', 'user-shift-migration', NULL, 300, NULL, 0, NULL, NULL, NULL, 'OPEN', 'user-shift-migration')");
                fail("Migration must prevent a second open shift for the same farm and user.");
            } catch (SQLiteConstraintException expected) {
                // The partial unique index is enforced by SQLite.
            }
            android.database.Cursor cursor = migratedSchema.query("SELECT COUNT(*) FROM shifts WHERE status = 'OPEN'");
            try { assertTrue(cursor.moveToFirst()); assertEquals(1, cursor.getInt(0)); } finally { cursor.close(); }
        } finally {
            migratedSchema.close();
        }
    }

    @Test public void shiftsAndAdjustmentRequestsPersistTheirStatusesAndActors() {
        ShiftEntity shift = newShift("shift-test");
        shift.status = ShiftStatus.CLOSED;
        shift.endedAtEpochMs = 800L;
        shift.closingCashMinorUnits = 50000L;
        shift.expectedCashMinorUnits = 49000L;
        shift.differenceCashMinorUnits = 1000L;
        database.shiftDao().insert(shift);
        assertEquals(ShiftStatus.CLOSED, database.shiftDao().findById(shift.shiftId).status);

        AdjustmentRequestEntity request = new AdjustmentRequestEntity();
        request.adjustmentRequestId = "adjustment-test";
        request.farmId = farm.farmId;
        request.requestedByUserId = user.userId;
        request.productId = ProductEntity.LARGE_ID;
        request.deviceId = device.deviceId;
        request.quantityDeltaEggs = -5;
        request.reason = "Cracked during transfer";
        request.status = AdjustmentStatus.PENDING;
        request.requestedAtEpochMs = 900;
        database.adjustmentRequestDao().insert(request);
        assertEquals(AdjustmentStatus.PENDING,
                database.adjustmentRequestDao().findById(request.adjustmentRequestId).status);
        assertEquals(user.userId,
                database.adjustmentRequestDao().getForFarmByStatus(farm.farmId, AdjustmentStatus.PENDING).get(0).requestedByUserId);
    }

    @Test public void outboxAndAuditRecordsPersistTheirReferenceMetadata() {
        SyncOutboxEntity queued = new SyncOutboxEntity();
        queued.farmId = farm.farmId;
        queued.deviceId = device.deviceId;
        queued.recordType = "SALE";
        queued.recordId = "sale-outbox";
        queued.operation = OutboxOperation.CREATE;
        queued.syncStatus = SyncStatus.PENDING;
        queued.idempotencyKey = "key-sale-outbox";
        database.syncOutboxDao().insert(queued);
        assertEquals(queued.outboxId,
                database.syncOutboxDao().getByStatus(farm.farmId, SyncStatus.PENDING).get(0).outboxId);

        AuditLogEntity audit = new AuditLogEntity();
        audit.farmId = farm.farmId;
        audit.actorUserId = user.userId;
        audit.deviceId = device.deviceId;
        audit.action = "PRICE_CREATED";
        audit.affectedType = "PRICE_VERSION";
        audit.affectedRecordId = "price-audit";
        audit.occurredAtEpochMs = 1000;
        audit.detailsJson = "{\"currency\":\"PHP\"}";
        database.auditLogDao().insert(audit);
        assertEquals("PRICE_CREATED", database.auditLogDao().findById(audit.auditLogId).action);
        assertEquals(1, database.auditLogDao().getForFarm(farm.farmId).size());
    }

    private PriceVersionEntity price(String id, int version, long amount, long from, Long to) {
        PriceVersionEntity price = new PriceVersionEntity();
        price.priceVersionId = id;
        price.farmId = farm.farmId;
        price.productId = ProductEntity.MEDIUM_ID;
        price.amountMinorUnits = amount;
        price.currencyCode = "PHP";
        price.effectiveFromEpochMs = from;
        price.effectiveToEpochMs = to;
        price.versionNumber = version;
        price.changedByUserId = user.userId;
        return price;
    }

    private ShiftEntity newShift(String id) {
        ShiftEntity shift = new ShiftEntity();
        shift.shiftId = id;
        shift.farmId = farm.farmId;
        shift.userId = user.userId;
        shift.deviceId = device.deviceId;
        shift.startedAtEpochMs = 200;
        shift.openingCashMinorUnits = 10000;
        return shift;
    }

    private SaleEntity newSale(String id, String receipt, String shiftId) {
        SaleEntity sale = new SaleEntity();
        sale.saleId = id;
        sale.receiptNumber = receipt;
        sale.farmId = farm.farmId;
        sale.actorUserId = user.userId;
        sale.shiftId = shiftId;
        sale.deviceId = device.deviceId;
        sale.soldAtEpochMs = 300;
        return sale;
    }

    private InventoryLedgerEntity ledger(String id, InventoryEventType type, String sourceType,
                                         String sourceId, int delta) {
        InventoryLedgerEntity entry = new InventoryLedgerEntity();
        entry.ledgerEntryId = id;
        entry.farmId = farm.farmId;
        entry.productId = ProductEntity.MEDIUM_ID;
        entry.eventType = type;
        entry.sourceType = sourceType;
        entry.sourceRecordId = sourceId;
        entry.actorUserId = user.userId;
        entry.deviceId = device.deviceId;
        entry.quantityDeltaEggs = delta;
        entry.occurredAtEpochMs = 400;
        return entry;
    }
}
