package com.example.syncore;

import android.content.Context;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PosUiIntegrationTest {
    @Test public void missingOperationalContextShowsSafeCatalogAndBlocksCheckout() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase("poultrytrack.db");
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.login_sign_in).performClick();
                activity.findViewById(R.id.nav_pos).performClick();
            });
            AtomicReference<String> message = new AtomicReference<>("");
            for (int attempt = 0; attempt < 30; attempt++) {
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity -> {
                    android.widget.TextView view = activity.findViewById(R.id.pos_context_message);
                    if (view != null) message.set(view.getText().toString());
                });
                if (message.get().contains("no existing open farm shift")) break;
                SystemClock.sleep(100);
            }
            assertTrue(message.get(), message.get().contains("no existing open farm shift"));
            scenario.onActivity(activity -> {
                TextView checkout = activity.findViewById(R.id.complete_sale);
                assertNotNull(checkout);
                assertFalse(checkout.isEnabled());
                ViewGroup products = activity.findViewById(R.id.pos_products);
                assertTrue("Database catalog should be visible", products.getChildCount() > 0);
                assertTrue("Unavailable database prices should be explicit", containsText(products, "Price and stock unavailable"));
            });
        }
    }

    private static boolean containsText(ViewGroup group, String expected) {
        for (int i = 0; i < group.getChildCount(); i++) {
            android.view.View child = group.getChildAt(i);
            if (child instanceof TextView && ((TextView) child).getText().toString().contains(expected)) return true;
            if (child instanceof ViewGroup && containsText((ViewGroup) child, expected)) return true;
        }
        return false;
    }
}
