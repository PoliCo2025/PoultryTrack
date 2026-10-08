package com.example.syncore.data.local;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.syncore.data.local.entity.ProductEntity;

/** Seeds only PoultryTrack's real reference catalog. No farm, user, sale, or demo activity is created. */
public final class DefaultEggCatalog {
    private static final String[][] PRODUCTS = {
            {ProductEntity.SMALL_ID, "Small", "0"},
            {ProductEntity.MEDIUM_ID, "Medium", "1"},
            {ProductEntity.LARGE_ID, "Large", "2"},
            {ProductEntity.EXTRA_LARGE_ID, "Extra Large", "3"}
    };

    private DefaultEggCatalog() { }

    public static void seed(SupportSQLiteDatabase database) {
        long now = System.currentTimeMillis();
        for (String[] product : PRODUCTS) {
            ContentValues values = new ContentValues();
            values.put("product_id", product[0]);
            values.put("name", product[1]);
            values.put("unit", "egg");
            values.put("sort_order", Integer.parseInt(product[2]));
            values.put("is_active", 1);
            values.put("created_at_epoch_ms", now);
            values.put("updated_at_epoch_ms", now);
            database.insert("products", SQLiteDatabase.CONFLICT_IGNORE, values);
        }
    }
}
