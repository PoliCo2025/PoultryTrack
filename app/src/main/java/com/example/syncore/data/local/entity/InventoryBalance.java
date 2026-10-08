package com.example.syncore.data.local.entity;

/** Read-only projection calculated from the inventory ledger; not a stored stock table. */
public class InventoryBalance {
    public String productId;
    public String productName;
    public long quantityOnHand;
}
