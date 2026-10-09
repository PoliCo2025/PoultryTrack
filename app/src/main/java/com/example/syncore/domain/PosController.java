package com.example.syncore.domain;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.repository.PosRepository;
import com.example.syncore.data.repository.PosRepository.PosCatalog;
import com.example.syncore.data.repository.PosRepository.SaleReceipt;

/** POS use-case boundary. Call every method from a background executor; UI code only renders its results. */
public final class PosController {
    private final PosRepository repository;
    private final SalesService salesService;
    private final PosCart cart = new PosCart();
    private PosCatalog catalog;

    public PosController(PoultryTrackDatabase database) {
        repository = new PosRepository(database);
        salesService = new SalesService(database);
    }

    public PosCatalog loadCatalog(long nowEpochMs) {
        catalog = repository.loadCatalog(nowEpochMs);
        return catalog;
    }

    public PosCatalog currentCatalog() { return catalog; }
    public PosCart cart() { return cart; }

    public SaleReceipt checkout(String saleId, String receiptNumber, long cashReceivedMinorUnits, long nowEpochMs) {
        // Recover a previously committed attempt before trying the service again with the same stable ID.
        SaleReceipt existing = repository.loadReceipt(saleId);
        if (existing != null) return existing;
        if (catalog == null || catalog.context == null)
            throw new IllegalStateException(catalog == null ? "POS data has not loaded." : catalog.message);
        if (cart.isEmpty()) throw new IllegalArgumentException("Add at least one item before checkout.");
        if (nowEpochMs < catalog.context.shiftStartedAtEpochMs)
            throw new IllegalStateException("The device clock is earlier than the open shift. Check the device time before checkout.");
        salesService.checkout(saleId, receiptNumber, catalog.context.farmId, catalog.context.userId,
                catalog.context.shiftId, catalog.context.deviceId, nowEpochMs, cart.snapshot(), cashReceivedMinorUnits);
        SaleReceipt persisted = repository.loadReceipt(saleId);
        if (persisted == null) throw new IllegalStateException("The sale was submitted but its saved receipt could not be loaded.");
        return persisted;
    }

    public SaleReceipt loadReceipt(String saleId) { return repository.loadReceipt(saleId); }
}
