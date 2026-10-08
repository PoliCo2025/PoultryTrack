package com.example.syncore.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.UUID;

/** A payment attached to a sale. Method names are strings so later methods can be added without a schema enum migration. */
@Entity(tableName = "payments",
        foreignKeys = @ForeignKey(entity = SaleEntity.class, parentColumns = "sale_id", childColumns = "sale_id",
                onDelete = ForeignKey.RESTRICT, onUpdate = ForeignKey.CASCADE),
        indices = {@Index(value = {"sale_id", "received_at_epoch_ms"})})
public class PaymentEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "payment_id") public String paymentId = UUID.randomUUID().toString();
    @NonNull @ColumnInfo(name = "sale_id") public String saleId = "";
    @NonNull @ColumnInfo(name = "payment_method") public String paymentMethod = "CASH";
    @ColumnInfo(name = "amount_minor_units") public long amountMinorUnits;
    @NonNull @ColumnInfo(name = "currency_code") public String currencyCode = "PHP";
    @ColumnInfo(name = "received_at_epoch_ms") public long receivedAtEpochMs;
}
