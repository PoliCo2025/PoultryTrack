package com.example.syncore.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
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
}, version = 1, exportSchema = true)
@TypeConverters(DatabaseConverters.class)
public abstract class PoultryTrackDatabase extends RoomDatabase {
    private static volatile PoultryTrackDatabase instance;

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
            }
        };
    }
}
