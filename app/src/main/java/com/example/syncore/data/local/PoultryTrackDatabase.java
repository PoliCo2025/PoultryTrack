package com.example.syncore.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.syncore.data.local.dao.AdjustmentRequestDao;
import com.example.syncore.data.local.dao.AuditLogDao;
import com.example.syncore.data.local.dao.DeviceDao;
import com.example.syncore.data.local.dao.FarmDao;
import com.example.syncore.data.local.dao.HarvestDao;
import com.example.syncore.data.local.dao.HarvestItemDao;
import com.example.syncore.data.local.dao.InventoryLedgerDao;
import com.example.syncore.data.local.dao.PaymentDao;
import com.example.syncore.data.local.dao.PriceVersionDao;
import com.example.syncore.data.local.dao.ProductDao;
import com.example.syncore.data.local.dao.SaleDao;
import com.example.syncore.data.local.dao.SaleItemDao;
import com.example.syncore.data.local.dao.ShiftDao;
import com.example.syncore.data.local.dao.SyncOutboxDao;
import com.example.syncore.data.local.dao.UserDao;
import com.example.syncore.data.local.entity.AdjustmentRequestEntity;
import com.example.syncore.data.local.entity.AuditLogEntity;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.HarvestEntity;
import com.example.syncore.data.local.entity.HarvestItemEntity;
import com.example.syncore.data.local.entity.InventoryLedgerEntity;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.local.entity.SaleItemEntity;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.SyncOutboxEntity;
import com.example.syncore.data.local.entity.UserEntity;

/** Version 1 is the initial schema. Future schema changes must use explicit migrations. */
@Database(entities = {
        FarmEntity.class,
        DeviceEntity.class,
        UserEntity.class,
        ProductEntity.class,
        PriceVersionEntity.class,
        ShiftEntity.class,
        SaleEntity.class,
        SaleItemEntity.class,
        PaymentEntity.class,
        HarvestEntity.class,
        HarvestItemEntity.class,
        AdjustmentRequestEntity.class,
        InventoryLedgerEntity.class,
        SyncOutboxEntity.class,
        AuditLogEntity.class
}, version = 4, exportSchema = true)
@TypeConverters(DatabaseConverters.class)
public abstract class PoultryTrackDatabase extends RoomDatabase {
    private static volatile PoultryTrackDatabase instance;

    /** Adds product-level source idempotency and makes account-name uniqueness farm-scoped. */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_inventory_ledger_event_type_source_type_source_record_id`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "`index_inventory_ledger_event_type_source_type_source_record_id_product_id` " +
                    "ON `inventory_ledger` (`event_type`, `source_type`, `source_record_id`, `product_id`)");
            database.execSQL("DROP INDEX IF EXISTS `index_users_username_normalized`");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_users_farm_id_username_normalized` " +
                    "ON `users` (`farm_id`, `username_normalized`)");
        }
    };

    /** At most one open cash shift per farm and staff member. Existing conflicts fail migration safely. */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `shifts` ADD COLUMN `open_user_id` TEXT");
            database.execSQL("UPDATE `shifts` SET `open_user_id` = `user_id` WHERE `status` = 'OPEN'");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shifts_farm_id_open_user_id` " +
                    "ON `shifts` (`farm_id`, `open_user_id`)");
        }
    };

    /** Prevents DAO callers from bypassing the open-shift unique slot with a missing or stale marker. */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(@NonNull SupportSQLiteDatabase database) {
            installShiftGuards(database);
        }
    };

    private static void installShiftGuards(@NonNull SupportSQLiteDatabase database) {
        database.execSQL("CREATE TRIGGER IF NOT EXISTS `trigger_shifts_open_slot_insert` " +
                "BEFORE INSERT ON `shifts` " +
                "WHEN (`NEW`.`status` = 'OPEN' AND (`NEW`.`open_user_id` IS NULL OR `NEW`.`open_user_id` != `NEW`.`user_id`)) " +
                "OR (`NEW`.`status` != 'OPEN' AND `NEW`.`open_user_id` IS NOT NULL) " +
                "BEGIN SELECT RAISE(ABORT, 'shift open-slot marker does not match status and user'); END");
        database.execSQL("CREATE TRIGGER IF NOT EXISTS `trigger_shifts_open_slot_update` " +
                "BEFORE UPDATE OF `status`, `user_id`, `farm_id`, `open_user_id` ON `shifts` " +
                "WHEN (`NEW`.`status` = 'OPEN' AND (`NEW`.`open_user_id` IS NULL OR `NEW`.`open_user_id` != `NEW`.`user_id`)) " +
                "OR (`NEW`.`status` != 'OPEN' AND `NEW`.`open_user_id` IS NOT NULL) " +
                "BEGIN SELECT RAISE(ABORT, 'shift open-slot marker does not match status and user'); END");
    }

    public abstract FarmDao farmDao();
    public abstract DeviceDao deviceDao();
    public abstract UserDao userDao();
    public abstract ProductDao productDao();
    public abstract PriceVersionDao priceVersionDao();
    public abstract ShiftDao shiftDao();
    public abstract SaleDao saleDao();
    public abstract SaleItemDao saleItemDao();
    public abstract PaymentDao paymentDao();
    public abstract HarvestDao harvestDao();
    public abstract HarvestItemDao harvestItemDao();
    public abstract AdjustmentRequestDao adjustmentRequestDao();
    public abstract InventoryLedgerDao inventoryLedgerDao();
    public abstract SyncOutboxDao syncOutboxDao();
    public abstract AuditLogDao auditLogDao();

    public static PoultryTrackDatabase getInstance(Context context) {
        PoultryTrackDatabase result = instance;
        if (result == null) {
            synchronized (PoultryTrackDatabase.class) {
                result = instance;
                if (result == null) {
                    result = Room.databaseBuilder(context.getApplicationContext(),
                                    PoultryTrackDatabase.class, "poultrytrack.db")
                            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                            .addCallback(initialCallback())
                            .build();
                    instance = result;
                }
            }
        }
        return result;
    }

    static RoomDatabase.Callback initialCallback() {
        return new RoomDatabase.Callback() {
            @Override public void onCreate(@NonNull SupportSQLiteDatabase database) {
                super.onCreate(database);
                DefaultEggCatalog.seed(database);
                installShiftGuards(database);
            }
        };
    }
}
