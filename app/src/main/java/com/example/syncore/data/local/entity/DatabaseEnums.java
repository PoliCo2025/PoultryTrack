package com.example.syncore.data.local.entity;

/** Persisted domain states. Keep values stable; add a database migration when changing stored meanings. */
public final class DatabaseEnums {
    private DatabaseEnums() { }

    public enum UserRole { ADMIN, SALES_PERSONNEL }
    public enum PriceStatus { SCHEDULED, ACTIVE, INACTIVE }
    public enum ShiftStatus { OPEN, CLOSED, RECONCILED }
    public enum SaleStatus { DRAFT, COMPLETED, VOIDED }
    public enum HarvestStatus { RECORDED, VOIDED }
    public enum AdjustmentStatus { PENDING, APPROVED, REJECTED }
    public enum InventoryEventType { HARVEST, SALE, APPROVED_ADJUSTMENT, OPENING_BALANCE }
    public enum OutboxOperation { CREATE, UPDATE, DELETE }
    public enum SyncStatus { PENDING, IN_PROGRESS, SYNCED, FAILED }
}
