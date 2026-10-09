package com.example.syncore.domain;

import com.example.syncore.data.local.entity.DatabaseEnums.PriceStatus;
import com.example.syncore.data.local.entity.PriceVersionEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.repository.PosRepository.PosCatalog;
import com.example.syncore.data.repository.PosRepository.PosProduct;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class PosCartTest {
    @Test public void quantityChangesUseRealIdsAndTotalsFollowLoadedPrice() {
        ProductEntity small = product(ProductEntity.SMALL_ID, "Small", true);
        ProductEntity medium = product(ProductEntity.MEDIUM_ID, "Medium", true);
        PosCart cart = new PosCart();
        PosCatalog catalog = catalog(new PosProduct(small, price(600), 10), new PosProduct(medium, price(700), 4));

        cart.adjust(catalog, ProductEntity.SMALL_ID, 1);
        cart.adjust(catalog, ProductEntity.SMALL_ID, 1);
        cart.adjust(catalog, ProductEntity.MEDIUM_ID, 1);
        assertEquals(Integer.valueOf(2), cart.snapshot().get(ProductEntity.SMALL_ID));
        assertEquals(1900L, cart.totalMinorUnits(catalog));

        cart.adjust(catalog, ProductEntity.SMALL_ID, -1);
        cart.adjust(catalog, ProductEntity.MEDIUM_ID, -1);
        assertEquals(600L, cart.totalMinorUnits(catalog));
        cart.adjust(catalog, ProductEntity.SMALL_ID, -1);
        assertTrue(cart.isEmpty());
    }

    @Test public void cartRejectsUnavailableProductsAndStockOverflow() {
        PosCart cart = new PosCart();
        PosCatalog catalog = catalog(
                new PosProduct(product(ProductEntity.SMALL_ID, "Small", true), price(600), 1),
                new PosProduct(product(ProductEntity.MEDIUM_ID, "Medium", true), null, 10),
                new PosProduct(product(ProductEntity.LARGE_ID, "Large", false), price(800), 10));
        cart.adjust(catalog, ProductEntity.SMALL_ID, 1);
        expectFailure(() -> cart.adjust(catalog, ProductEntity.SMALL_ID, 1));
        expectFailure(() -> cart.adjust(catalog, ProductEntity.MEDIUM_ID, 1));
        expectFailure(() -> cart.adjust(catalog, ProductEntity.LARGE_ID, 1));
        expectFailure(() -> cart.adjust(catalog, ProductEntity.SMALL_ID, 2));
        cart.restore(Collections.singletonMap(ProductEntity.SMALL_ID, -3));
        assertTrue(cart.isEmpty());
    }

    private static PosCatalog catalog(PosProduct... products) { return new PosCatalog(null, Arrays.asList(products), null); }
    private static ProductEntity product(String id, String name, boolean active) {
        ProductEntity product = new ProductEntity(); product.productId = id; product.name = name; product.active = active; return product;
    }
    private static PriceVersionEntity price(long minorUnits) {
        PriceVersionEntity price = new PriceVersionEntity(); price.amountMinorUnits = minorUnits; price.currencyCode = "PHP"; price.status = PriceStatus.ACTIVE; return price;
    }
    private static void expectFailure(Runnable action) {
        try { action.run(); fail("Expected invalid cart operation to be rejected."); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
}
