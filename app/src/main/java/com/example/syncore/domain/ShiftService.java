package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.ShiftEntity;
import com.example.syncore.data.local.entity.DatabaseEnums.ShiftStatus;

/** Starts one staff cash session at a time and records its cash reconciliation. */
public final class ShiftService {
    private final PoultryTrackDatabase db;
    public ShiftService(PoultryTrackDatabase db) { this.db = db; }

    public ShiftEntity start(String farmId, String userId, String deviceId, long openingCashMinorUnits,
                             long startedAtEpochMs) {
        if (openingCashMinorUnits < 0 || startedAtEpochMs < 0) throw new IllegalArgumentException("Opening cash and time cannot be negative.");
        final ShiftEntity[] result = new ShiftEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId);
            BusinessRules.user(db, farmId, userId);
            BusinessRules.device(db, farmId, deviceId);
            if (db.shiftDao().findOpenForUser(farmId, userId) != null) throw new IllegalStateException("This user already has an open shift.");
            ShiftEntity shift = new ShiftEntity();
            shift.farmId = farmId; shift.userId = userId; shift.deviceId = deviceId;
            shift.openingCashMinorUnits = openingCashMinorUnits; shift.startedAtEpochMs = startedAtEpochMs;
            shift.status = ShiftStatus.OPEN; shift.openUserId = userId;
            db.shiftDao().insert(shift); result[0] = shift;
        });
        return result[0];
    }

    public ShiftEntity close(String farmId, String shiftId, String userId, long closingCashMinorUnits,
                             long endedAtEpochMs, String currencyCode) {
        if (closingCashMinorUnits < 0 || endedAtEpochMs < 0) throw new IllegalArgumentException("Closing cash and time cannot be negative.");
        if (!SalesService.DEFAULT_CURRENCY.equals(currencyCode)) throw new IllegalArgumentException("Shift reconciliation currently supports PHP only.");
        final ShiftEntity[] result = new ShiftEntity[1];
        db.runInTransaction(() -> {
            BusinessRules.farm(db, farmId); BusinessRules.user(db, farmId, userId);
            ShiftEntity shift = db.shiftDao().findById(shiftId);
            if (shift == null || !farmId.equals(shift.farmId) || !userId.equals(shift.userId)) throw new IllegalArgumentException("Shift does not belong to this farm and user.");
            if (shift.status != ShiftStatus.OPEN) throw new IllegalStateException("Only an open shift can be closed.");
            if (endedAtEpochMs < shift.startedAtEpochMs) throw new IllegalArgumentException("End time precedes shift start.");
            Long latestSaleAt = db.shiftDao().getLatestCompletedSaleTime(farmId, shiftId);
            if (latestSaleAt != null && endedAtEpochMs < latestSaleAt)
                throw new IllegalArgumentException("Shift end time precedes a completed sale.");
            long cashSales = db.shiftDao().getCashPaidForShift(farmId, shiftId, currencyCode);
            long expected = Math.addExact(shift.openingCashMinorUnits, cashSales);
            shift.endedAtEpochMs = endedAtEpochMs; shift.closingCashMinorUnits = closingCashMinorUnits;
            shift.expectedCashMinorUnits = expected; shift.differenceCashMinorUnits = Math.subtractExact(closingCashMinorUnits, expected);
            shift.status = ShiftStatus.CLOSED; shift.openUserId = null;
            db.shiftDao().update(shift); result[0] = shift;
        });
        return result[0];
    }
}
