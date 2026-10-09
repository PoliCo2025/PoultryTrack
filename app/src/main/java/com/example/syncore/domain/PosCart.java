package com.example.syncore.domain;

import com.example.syncore.data.repository.PosRepository.PosCatalog;
import com.example.syncore.data.repository.PosRepository.PosProduct;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** In-memory cart keyed by real catalog product IDs; prices and totals always come from the loaded catalog. */
public final class PosCart {
    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    public void adjust(PosCatalog catalog, String productId, int delta) {
        if (delta != 1 && delta != -1) throw new IllegalArgumentException("Cart quantity can change by one egg at a time.");
        PosProduct product = find(catalog, productId);
        if (!product.canSell()) throw new IllegalStateException(unavailableReason(product));
        int current = quantity(productId);
        int next = Math.addExact(current, delta);
        if (next < 0) next = 0;
        if (next > product.stockEggs) throw new IllegalStateException("Only " + product.stockEggs + " eggs are currently in stock for " + product.product.name + ".");
        if (next == 0) quantities.remove(productId); else quantities.put(productId, next);
    }

    public int quantity(String productId) { Integer value = quantities.get(productId); return value == null ? 0 : value; }
    public boolean isEmpty() { return quantities.isEmpty(); }
    public Map<String, Integer> snapshot() { return Collections.unmodifiableMap(new LinkedHashMap<>(quantities)); }
    public void restore(Map<String, Integer> saved) {
        quantities.clear();
        if (saved == null) return;
        for (Map.Entry<String, Integer> entry : saved.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().trim().isEmpty()
                    && entry.getValue() != null && entry.getValue() > 0) quantities.put(entry.getKey(), entry.getValue());
        }
    }
    public void clear() { quantities.clear(); }

    public long totalMinorUnits(PosCatalog catalog) {
        long total = 0;
        for (Map.Entry<String, Integer> line : quantities.entrySet()) {
            PosProduct product = find(catalog, line.getKey());
            if (!product.canSell()) throw new IllegalStateException(unavailableReason(product));
            total = Math.addExact(total, Math.multiplyExact(product.price.amountMinorUnits, line.getValue().longValue()));
        }
        return total;
    }

    private static PosProduct find(PosCatalog catalog, String productId) {
        if (catalog == null || catalog.products == null) throw new IllegalStateException("POS products have not loaded.");
        for (PosProduct product : catalog.products) if (product.product.productId.equals(productId)) return product;
        throw new IllegalArgumentException("Product is not in the active catalog.");
    }

    private static String unavailableReason(PosProduct product) {
        if (!product.product.active) return product.product.name + " is inactive and cannot be sold.";
        if (product.price == null) return "No current price is available for " + product.product.name + ".";
        if (!"PHP".equals(product.price.currencyCode)) return "Only PHP prices can be sold in this phase.";
        return "No " + product.product.name + " eggs are currently in stock.";
    }
}
