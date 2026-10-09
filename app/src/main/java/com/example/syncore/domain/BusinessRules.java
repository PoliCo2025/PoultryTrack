package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.DeviceEntity;
import com.example.syncore.data.local.entity.FarmEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.UserEntity;

/** Shared validation for the local business-services layer. */
final class BusinessRules {
    private BusinessRules() { }

    static FarmEntity farm(PoultryTrackDatabase db, String id) {
        FarmEntity value = id == null ? null : db.farmDao().findById(id);
        if (value == null || !value.active) throw new IllegalArgumentException("Farm is missing or inactive.");
        return value;
    }
    static UserEntity user(PoultryTrackDatabase db, String farmId, String id) {
        UserEntity value = id == null ? null : db.userDao().findById(id);
        if (value == null || !value.active || !farmId.equals(value.farmId))
            throw new IllegalArgumentException("User does not belong to this active farm.");
        return value;
    }
    static ProductEntity product(PoultryTrackDatabase db, String id) {
        ProductEntity value = id == null ? null : db.productDao().findById(id);
        if (value == null || !value.active) throw new IllegalArgumentException("Product is missing or inactive.");
        return value;
    }
    static void device(PoultryTrackDatabase db, String farmId, String id) {
        if (id == null) return;
        DeviceEntity value = db.deviceDao().findById(id);
        if (value == null || !farmId.equals(value.farmId))
            throw new IllegalArgumentException("Device does not belong to this farm.");
    }
}
