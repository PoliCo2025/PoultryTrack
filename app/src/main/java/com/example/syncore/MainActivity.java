package com.example.syncore;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewStub;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.syncore.data.local.PoultryTrackDatabase;
import com.example.syncore.data.local.entity.PaymentEntity;
import com.example.syncore.data.local.entity.ProductEntity;
import com.example.syncore.data.local.entity.SaleEntity;
import com.example.syncore.data.repository.PosRepository.PosCatalog;
import com.example.syncore.data.repository.PosRepository.PosProduct;
import com.example.syncore.data.repository.PosRepository.ReceiptItem;
import com.example.syncore.data.repository.PosRepository.SaleReceipt;
import com.example.syncore.domain.PosController;
import com.example.syncore.domain.PosCart;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Routes between PoultryTrack's XML-defined screens and their visual states. */
public class MainActivity extends AppCompatActivity {
    private static final int GREEN = Color.rgb(40, 93, 59);
    private static final int MUTED = Color.rgb(100, 112, 103);
    private static final int CHARCOAL = Color.rgb(39, 52, 43);
    private static final ExecutorService POS_IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "poultrytrack-pos-io"));
    private String current = "login";
    private final Deque<String> backStack = new ArrayDeque<>();
    private PosController posController;
    private PosCatalog posCatalog;
    private boolean posLoading;
    private boolean checkoutRunning;
    private boolean checkoutConfirmationOpen;
    private String posError;
    private String cashInput = "";
    private String pendingSaleId;
    private String pendingReceiptNumber;
    private String selectedReceiptId;
    private int posLoadGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        posController = new PosController(PoultryTrackDatabase.getInstance(getApplicationContext()));
        if (state != null) {
            current = state.getString("screen", "login");
            String[] stack = state.getStringArray("back_stack");
            if (stack != null) for (int i = stack.length - 1; i >= 0; i--) backStack.push(stack[i]);
            String[] productIds = state.getStringArray("cart_product_ids");
            int[] quantities = state.getIntArray("cart_quantities");
            Map<String, Integer> savedCart = new LinkedHashMap<>();
            if (productIds != null && quantities != null) for (int i = 0; i < Math.min(productIds.length, quantities.length); i++)
                savedCart.put(productIds[i], quantities[i]);
            posController.cart().restore(savedCart);
            cashInput = state.getString("cash_input", "");
            pendingSaleId = state.getString("pending_sale_id");
            pendingReceiptNumber = state.getString("pending_receipt_number");
            selectedReceiptId = state.getString("selected_receipt_id");
        }
        getWindow().setStatusBarColor(Color.rgb(244, 246, 242));
        getWindow().setNavigationBarColor(Color.rgb(244, 246, 242));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!backStack.isEmpty()) {
                    goBack();
                } else if (!current.equals("dashboard") && !current.equals("login")) {
                    show("dashboard");
                } else {
                    setEnabled(false);
                    try {
                        getOnBackPressedDispatcher().onBackPressed();
                    } finally {
                        setEnabled(true);
                    }
                }
            }
        });
        show(state == null ? "login" : current);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("screen", current);
        outState.putStringArray("back_stack", backStack.toArray(new String[0]));
        Map<String, Integer> quantities = posController.cart().snapshot();
        outState.putStringArray("cart_product_ids", quantities.keySet().toArray(new String[0]));
        int[] count = new int[quantities.size()];
        int index = 0;
        for (Integer value : quantities.values()) count[index++] = value;
        outState.putIntArray("cart_quantities", count);
        outState.putString("cash_input", cashInput);
        outState.putString("pending_sale_id", pendingSaleId);
        outState.putString("pending_receipt_number", pendingReceiptNumber);
        outState.putString("selected_receipt_id", selectedReceiptId);
    }

    private void navigate(String screen) {
        if (!screen.equals(current)) backStack.push(current);
        show(screen);
    }

    private void configurePosScreen() {
        EditText cash = findViewById(R.id.cash_received);
        if (cash != null) {
            cash.setText(cashInput);
            cash.setSelection(cash.length());
            cash.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    cashInput = s.toString();
                    renderPosSummary();
                    clearPosError();
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
        View retry = findViewById(R.id.pos_retry);
        if (retry != null) retry.setOnClickListener(v -> { posCatalog = null; loadPosData(); });
        renderPos();
        loadPosData();
    }

    private void loadPosData() {
        if (posLoading) return;
        posLoading = true;
        int generation = ++posLoadGeneration;
        renderPos();
        POS_IO.execute(() -> {
            PosCatalog catalog = null;
            SaleReceipt recovered = null;
            String failure = null;
            try {
                if (pendingSaleId != null) recovered = posController.loadReceipt(pendingSaleId);
                if (recovered == null) catalog = posController.loadCatalog(System.currentTimeMillis());
            } catch (RuntimeException ex) { failure = messageFor(ex); }
            PosCatalog loadedCatalog = catalog;
            SaleReceipt loadedReceipt = recovered;
            String loadFailure = failure;
            runOnUiThread(() -> {
                if (generation != posLoadGeneration || isFinishing()) return;
                posLoading = false;
                if (loadedReceipt != null) {
                    finishPersistedSale(loadedReceipt, current.equals("pos"));
                    return;
                }
                posCatalog = loadedCatalog;
                if (loadFailure != null) posError = loadFailure;
                TextView subtitle = findViewById(R.id.page_subtitle);
                if (subtitle != null && current.equals("pos") && loadedCatalog != null && loadedCatalog.context != null)
                    subtitle.setText(loadedCatalog.context.farmName + " · " + loadedCatalog.context.userName);
                renderPos();
            });
        });
    }

    private void renderPos() {
        if (!current.equals("pos")) return;
        View loading = findViewById(R.id.pos_loading);
        if (loading != null) loading.setVisibility(posLoading || checkoutRunning ? View.VISIBLE : View.GONE);
        TextView notice = findViewById(R.id.pos_context_message);
        if (notice != null) {
            String message = posCatalog == null ? "Loading farm products, current prices, and stock…" : posCatalog.message;
            if (message == null && posCatalog.context != null) {
                boolean anyPrice = false;
                for (PosProduct product : posCatalog.products) if (product.canSell()) { anyPrice = true; break; }
                if (!anyPrice) message = "No active PHP prices and available stock are ready for sale. Add real prices and record inventory before checkout.";
            }
            notice.setText(message == null ? "" : message);
            notice.setVisibility(message == null ? View.GONE : View.VISIBLE);
        }
        LinearLayout products = findViewById(R.id.pos_products);
        if (products != null) {
            products.removeAllViews();
            if (posCatalog != null) for (PosProduct product : posCatalog.products) products.addView(createProductRow(product));
        }
        TextView emptyProducts = findViewById(R.id.pos_empty_products);
        if (emptyProducts != null) emptyProducts.setVisibility(posCatalog != null && posCatalog.products.isEmpty() ? View.VISIBLE : View.GONE);
        renderCartLines();
        renderPosSummary();
        TextView submit = findViewById(R.id.complete_sale);
        if (submit != null) {
            boolean canSubmit = !posLoading && !checkoutRunning && posCatalog != null && posCatalog.context != null
                    && !posController.cart().isEmpty() && cartCanBeSubmitted();
            submit.setEnabled(canSubmit);
            submit.setAlpha(canSubmit ? 1f : 0.55f);
            submit.setText(checkoutRunning ? "Saving sale…" : "✓  Complete cash sale · " + phpText(currentCartTotal()));
        }
        TextView error = findViewById(R.id.pos_error);
        if (error != null) {
            error.setText(posError == null ? "" : posError);
            error.setVisibility(posError == null ? View.GONE : View.VISIBLE);
        }
    }

    private View createProductRow(PosProduct item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(7), dp(8), dp(7));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(62));
        cardParams.bottomMargin = dp(6);
        card.setLayoutParams(cardParams);
        card.setBackgroundResource(R.drawable.login_card);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = new TextView(this); name.setText(item.product.name); name.setTextColor(CHARCOAL); name.setTextSize(12); name.setTypeface(null, android.graphics.Typeface.BOLD);
        TextView price = new TextView(this); price.setText(productDescription(item)); price.setTextColor(MUTED); price.setTextSize(9);
        details.addView(name); details.addView(price); card.addView(details);

        int quantity = posController.cart().quantity(item.product.productId);
        TextView minus = controlButton("−", "Remove one " + item.product.name + " egg");
        TextView count = new TextView(this); count.setText(String.valueOf(quantity)); count.setGravity(Gravity.CENTER); count.setTextColor(CHARCOAL); count.setTextSize(12);
        count.setLayoutParams(new LinearLayout.LayoutParams(dp(30), dp(38)));
        TextView plus = controlButton("＋", "Add one " + item.product.name + " egg");
        boolean enabled = !checkoutRunning && !posLoading && posCatalog != null && posCatalog.context != null && item.canSell();
        minus.setEnabled(enabled && quantity > 0); minus.setAlpha(minus.isEnabled() ? 1f : 0.4f);
        plus.setEnabled(enabled && quantity < item.stockEggs); plus.setAlpha(plus.isEnabled() ? 1f : 0.4f);
        minus.setOnClickListener(v -> adjustCart(item.product.productId, -1));
        plus.setOnClickListener(v -> adjustCart(item.product.productId, 1));
        card.addView(minus); card.addView(count); card.addView(plus);
        return card;
    }

    private TextView controlButton(String text, String description) {
        TextView button = new TextView(this);
        button.setText(text); button.setContentDescription(description); button.setGravity(Gravity.CENTER);
        button.setTextColor(GREEN); button.setTextSize(18);
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(38)));
        return button;
    }

    private String productDescription(PosProduct item) {
        if (!item.product.active) return "Inactive · cannot be sold";
        if (posCatalog == null || posCatalog.context == null) return "Price and stock unavailable · open farm shift required";
        if (item.price == null) return "No current price · " + item.stockEggs + " eggs in stock";
        if (!"PHP".equals(item.price.currencyCode)) return "Unsupported currency · " + item.stockEggs + " eggs in stock";
        return phpText(item.price.amountMinorUnits) + " / egg · " + item.stockEggs + " eggs in stock";
    }

    private void adjustCart(String productId, int delta) {
        try {
            posController.cart().adjust(posCatalog, productId, delta);
            clearPosError();
        } catch (RuntimeException ex) { posError = messageFor(ex); }
        renderPos();
    }

    private void renderCartLines() {
        LinearLayout lines = findViewById(R.id.pos_cart_lines);
        TextView empty = findViewById(R.id.pos_empty_cart);
        if (lines == null) return;
        lines.removeAllViews();
        Map<String, Integer> cart = posController.cart().snapshot();
        if (empty != null) empty.setVisibility(cart.isEmpty() ? View.VISIBLE : View.GONE);
        for (Map.Entry<String, Integer> entry : cart.entrySet()) {
            PosProduct product = findPosProduct(entry.getKey());
            String name = product == null ? entry.getKey() : product.product.name;
            String unit = product == null || product.price == null ? "Price unavailable" : phpText(product.price.amountMinorUnits);
            String line = product == null || product.price == null ? "" : "  ·  " + phpText(Math.multiplyExact(product.price.amountMinorUnits, entry.getValue().longValue()));
            TextView row = new TextView(this);
            row.setText(name + "  ·  " + entry.getValue() + " eggs × " + unit + line);
            row.setTextColor(CHARCOAL); row.setTextSize(10); row.setPadding(0, dp(3), 0, dp(3));
            lines.addView(row);
        }
    }

    private void renderPosSummary() {
        TextView total = findViewById(R.id.pos_total);
        long amount = currentCartTotal();
        if (total != null) total.setText(phpText(amount));
        TextView change = findViewById(R.id.pos_change);
        if (change != null) {
            try {
                long received = parseCash(cashInput);
                change.setText(received >= amount ? "Change · " + phpText(received - amount) : "Amount due · " + phpText(amount - received));
            } catch (RuntimeException ex) { change.setText("Change · Enter valid cash received"); }
        }
    }

    private long currentCartTotal() {
        try { return posController.cart().totalMinorUnits(posCatalog); }
        catch (RuntimeException ignored) { return 0; }
    }

    private PosProduct findPosProduct(String productId) {
        if (posCatalog == null) return null;
        for (PosProduct product : posCatalog.products) if (product.product.productId.equals(productId)) return product;
        return null;
    }

    private void confirmPosCheckout() {
        if (checkoutRunning || checkoutConfirmationOpen) return;
        if (posCatalog == null || posCatalog.context == null) { posError = posCatalog == null ? "POS data is still loading." : posCatalog.message; renderPos(); return; }
        if (posController.cart().isEmpty()) { posError = "Add at least one item before checkout."; renderPos(); return; }
        final long total;
        final long cash;
        try { total = posController.cart().totalMinorUnits(posCatalog); cash = parseCash(cashInput); }
        catch (RuntimeException ex) { posError = messageFor(ex); renderPos(); return; }
        if (cash < total) { posError = "Cash received must be at least " + phpText(total) + "."; renderPos(); return; }
        View content = getLayoutInflater().inflate(R.layout.pt_confirm_dialog, null, false);
        ((TextView) content.findViewById(R.id.confirmation_title)).setText("Complete this sale?");
        ((TextView) content.findViewById(R.id.confirmation_message)).setText(
                "Charge " + phpText(total) + " in cash and save the sale on this device?");
        checkoutConfirmationOpen = true;
        new AlertDialog.Builder(this).setView(content)
                .setNegativeButton("Cancel", (dialog, which) -> checkoutConfirmationOpen = false)
                .setPositiveButton("Complete sale", (dialog, which) -> {
                    checkoutConfirmationOpen = false;
                    performCheckout(cash);
                })
                .setOnCancelListener(dialog -> checkoutConfirmationOpen = false)
                .show();
    }

    private void performCheckout(long cashMinorUnits) {
        if (checkoutRunning) return;
        if (pendingSaleId == null) {
            pendingSaleId = UUID.randomUUID().toString();
            pendingReceiptNumber = "PT-" + Long.toString(System.currentTimeMillis(), 36).toUpperCase(Locale.ROOT)
                    + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        }
        checkoutRunning = true; posError = null; renderPos();
        String saleId = pendingSaleId;
        String receipt = pendingReceiptNumber;
        long now = System.currentTimeMillis();
        POS_IO.execute(() -> {
            SaleReceipt result = null;
            String failure = null;
            try { result = posController.checkout(saleId, receipt, cashMinorUnits, now); }
            catch (RuntimeException ex) {
                try { result = posController.loadReceipt(saleId); }
                catch (RuntimeException lookupFailure) { failure = "Checkout outcome could not be confirmed. Keep this attempt unchanged and retry to safely check its saved receipt. " + messageFor(lookupFailure); }
                if (result == null && failure == null) failure = messageFor(ex);
            }
            SaleReceipt saved = result;
            String error = failure;
            runOnUiThread(() -> {
                checkoutRunning = false;
                if (isFinishing()) return;
                if (saved != null) finishPersistedSale(saved, current.equals("pos"));
                else { posError = error; if (current.equals("pos")) renderPos(); }
            });
        });
    }

    private void finishPersistedSale(SaleReceipt receipt, boolean openNow) {
        selectedReceiptId = receipt.sale.saleId;
        posController.cart().clear();
        cashInput = "";
        posError = null; checkoutRunning = false;
        if (openNow) {
            pendingSaleId = null; pendingReceiptNumber = null;
            navigate("sale_complete");
        }
    }

    private void configureReceiptScreen() {
        View loading = findViewById(R.id.receipt_loading);
        View details = findViewById(R.id.receipt_details);
        View error = findViewById(R.id.receipt_error);
        View start = findViewById(R.id.start_sale);
        View shift = findViewById(R.id.view_shift);
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (details != null) details.setVisibility(View.GONE);
        if (error != null) error.setVisibility(View.GONE);
        if (start != null) start.setVisibility(View.GONE);
        if (shift != null) shift.setVisibility(View.GONE);
        if (selectedReceiptId == null) { showReceiptError("This receipt has no saved sale identifier."); return; }
        String saleId = selectedReceiptId;
        POS_IO.execute(() -> {
            SaleReceipt receipt = null; String failure = null;
            try { receipt = posController.loadReceipt(saleId); }
            catch (RuntimeException ex) { failure = messageFor(ex); }
            SaleReceipt loaded = receipt; String message = failure;
            runOnUiThread(() -> {
                if (isFinishing() || !current.equals("sale_complete") || !saleId.equals(selectedReceiptId)) return;
                if (loaded == null) showReceiptError(message == null ? "The saved receipt could not be found on this device." : message);
                else renderReceipt(loaded);
            });
        });
    }

    private void renderReceipt(SaleReceipt receipt) {
        View loading = findViewById(R.id.receipt_loading); if (loading != null) loading.setVisibility(View.GONE);
        View error = findViewById(R.id.receipt_error); if (error != null) error.setVisibility(View.GONE);
        View details = findViewById(R.id.receipt_details); if (details != null) details.setVisibility(View.VISIBLE);
        View start = findViewById(R.id.start_sale); if (start != null) start.setVisibility(View.VISIBLE);
        View shift = findViewById(R.id.view_shift); if (shift != null) shift.setVisibility(View.VISIBLE);
        SaleEntity sale = receipt.sale;
        TextView total = findViewById(R.id.receipt_total); if (total != null) total.setText(phpText(sale.totalMinorUnits) + " received");
        TextView status = findViewById(R.id.receipt_status);
        if (status != null) status.setText("Cash sale · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(sale.soldAtEpochMs)
                + "\n" + sale.status.name() + " · Saved on this device");
        TextView farm = findViewById(R.id.receipt_farm); if (farm != null) farm.setText(receipt.farmName);
        TextView number = findViewById(R.id.receipt_number);
        if (number != null) number.setText("Receipt " + sale.receiptNumber + " · " + receipt.actorName);
        LinearLayout itemLines = findViewById(R.id.receipt_items);
        if (itemLines != null) {
            itemLines.removeAllViews();
            for (ReceiptItem item : receipt.items) {
                TextView line = new TextView(this);
                line.setText(item.productName + " · " + item.saleItem.quantityEggs + " eggs × "
                        + phpText(item.saleItem.unitPriceMinorUnits) + "  ·  " + phpText(item.saleItem.lineTotalMinorUnits));
                line.setTextColor(CHARCOAL); line.setTextSize(10); line.setPadding(0, dp(3), 0, dp(3)); itemLines.addView(line);
            }
        }
        TextView payment = findViewById(R.id.receipt_payment);
        if (payment != null) {
            int totalEggs = 0;
            for (ReceiptItem item : receipt.items) totalEggs = Math.addExact(totalEggs, item.saleItem.quantityEggs);
            StringBuilder text = new StringBuilder("Subtotal  ").append(phpText(sale.subtotalMinorUnits))
                    .append("\nTotal  ").append(phpText(sale.totalMinorUnits))
                    .append("  ·  ").append(totalEggs).append(" eggs");
            for (PaymentEntity row : receipt.payments) text.append("\n").append(row.paymentMethod).append(" paid  ").append(phpText(row.amountMinorUnits));
            if (sale.cashReceivedMinorUnits != null) text.append("\nCash received  ").append(phpText(sale.cashReceivedMinorUnits));
            if (sale.changeMinorUnits != null) text.append("\nChange  ").append(phpText(sale.changeMinorUnits));
            payment.setText(text);
        }
    }

    private void showReceiptError(String message) {
        View loading = findViewById(R.id.receipt_loading); if (loading != null) loading.setVisibility(View.GONE);
        View details = findViewById(R.id.receipt_details); if (details != null) details.setVisibility(View.GONE);
        View start = findViewById(R.id.start_sale); if (start != null) start.setVisibility(View.GONE);
        View error = findViewById(R.id.receipt_error);
        if (error instanceof TextView) { ((TextView) error).setText(message); error.setVisibility(View.VISIBLE); }
    }

    private void startAnotherSale() {
        posController.cart().clear(); posCatalog = null; cashInput = ""; posError = null;
        pendingSaleId = null; pendingReceiptNumber = null;
        navigate("pos");
    }

    private long parseCash(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Enter the cash received.");
        try { return new BigDecimal(value.replace(",", "").trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact(); }
        catch (NumberFormatException | ArithmeticException ex) { throw new IllegalArgumentException("Enter a valid PHP amount with up to two decimal places."); }
    }

    private String phpText(long minorUnits) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        format.setCurrency(java.util.Currency.getInstance("PHP"));
        format.setMinimumFractionDigits(2); format.setMaximumFractionDigits(2);
        return format.format(minorUnits / 100.0d);
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private static String messageFor(Throwable error) {
        return error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? "The local operation could not be completed. Your cart has been retained." : error.getMessage();
    }
    private boolean cartCanBeSubmitted() {
        try { return posController.cart().totalMinorUnits(posCatalog) > 0; }
        catch (RuntimeException ignored) { return false; }
    }

    private void clearPosError() {
        posError = null;
        View error = findViewById(R.id.pos_error);
        if (error != null) error.setVisibility(View.GONE);
    }

    private void show(String screen) {
        current = screen;
        setContentView(layoutFor(screen));
        String[] title = titleFor(screen);
        TextView heading = findViewById(R.id.page_title);
        TextView subtitle = findViewById(R.id.page_subtitle);
        View pageBack = findViewById(R.id.page_back);
        if (heading != null) heading.setText(title[0]);
        if (subtitle != null) subtitle.setText(title[1]);
        if (pageBack != null) pageBack.setVisibility(screen.equals("dashboard") ? View.GONE : View.VISIBLE);
        updateBottomNavigation();
        configureEmptyState(R.id.dashboard_empty_state, "No sales yet", "Completed sales will appear here.");
        configureEmptyState(R.id.inventory_empty_state, "No inventory records", "Record a harvest to start tracking stock.");
        configureEmptyState(R.id.harvest_empty_state, "No harvest records", "Recorded harvests will appear here.");
        configureEmptyState(R.id.reports_empty_state, "No report data", "Choose another date or add records first.");
        configureEmptyState(R.id.users_empty_state, "No users added", "Add a user to manage farm access.");

        bind(R.id.page_back, this::goBack);
        bind(R.id.nav_home, () -> navigate("dashboard"));
        bind(R.id.nav_pos, () -> navigate("pos"));
        bind(R.id.nav_inventory, () -> navigate("inventory"));
        bind(R.id.nav_reports, () -> navigate("reports"));
        bind(R.id.nav_more, () -> navigate("more"));

        bind(R.id.new_sale, () -> navigate("pos"));
        bind(R.id.complete_sale, this::confirmPosCheckout);
        bind(R.id.start_sale, this::startAnotherSale);
        bind(R.id.view_shift, () -> navigate("shift_summary"));
        bind(R.id.record_harvest, () -> navigate("harvest"));
        bind(R.id.add_harvest, () -> navigate("harvest"));
        bind(R.id.report_loss, () -> navigate("adjustment"));
        bind(R.id.save_harvest, () -> confirm("Record this harvest?",
                "Review the egg counts before submitting this harvest entry.",
                "Record harvest", () -> { toast("Harvest saved on this device"); navigate("inventory"); }));
        bind(R.id.approve_adjustment, () -> confirm("Approve this adjustment?",
                "The displayed quantity will be deducted from the selected egg size.",
                "Approve", () -> { toast("Adjustment approved"); navigate("inventory"); }));
        bind(R.id.reject_adjustment, () -> confirm("Reject this adjustment?",
                "This request will be marked as rejected.",
                "Reject", () -> { toast("Request rejected"); navigate("more"); }));
        bind(R.id.users, () -> navigate("users"));
        bind(R.id.pricing, () -> navigate("pricing"));
        bind(R.id.adjustment, () -> navigate("adjustment"));
        bind(R.id.sync_status, () -> navigate("sync_status"));
        bind(R.id.shift_summary, () -> navigate("shift_summary"));
        bind(R.id.reports, () -> navigate("reports"));
        bind(R.id.save_prices, () -> confirm("Save these prices?",
                "The new prices will be used for future sales.",
                "Save prices", () -> toast("Prices saved on this device")));
        bind(R.id.backup, () -> toast("Backup will run when internet is available"));
        bind(R.id.export_report, () -> toast("Report export prepared"));
        bind(R.id.share_report, () -> toast("Report is ready to share"));
        bind(R.id.export_shift, () -> toast("Shift summary export prepared"));
        bind(R.id.share_shift, () -> toast("Shift summary is ready to share"));
        bind(R.id.add_user, () -> toast("User form is not connected yet"));
        bind(R.id.sign_out, () -> confirm("Sign out?", "You can sign in again on this device.",
                "Sign out", () -> toast("Sign out is not connected yet")));
        bind(R.id.login_sign_in, () -> navigate("dashboard"));
        if (screen.equals("pos")) configurePosScreen();
        else if (screen.equals("sale_complete")) configureReceiptScreen();
    }

    private void updateBottomNavigation() {
        String selected = selectedTabFor(current);
        setTab(R.id.nav_home, R.id.nav_home_icon, R.id.nav_home_label, "home", selected);
        setTab(R.id.nav_pos, R.id.nav_pos_icon, R.id.nav_pos_label, "pos", selected);
        setTab(R.id.nav_inventory, R.id.nav_inventory_icon, R.id.nav_inventory_label, "inventory", selected);
        setTab(R.id.nav_reports, R.id.nav_reports_icon, R.id.nav_reports_label, "reports", selected);
        setTab(R.id.nav_more, R.id.nav_more_icon, R.id.nav_more_label, "more", selected);
    }

    private String selectedTabFor(String screen) {
        if (screen.equals("dashboard")) return "home";
        if (screen.equals("pos") || screen.equals("sale_complete")) return "pos";
        if (screen.equals("inventory") || screen.equals("harvest") || screen.equals("adjustment")) return "inventory";
        if (screen.equals("reports") || screen.equals("shift_summary")) return "reports";
        return "more";
    }

    private void setTab(int containerId, int iconId, int labelId, String tab, String selected) {
        View container = findViewById(containerId);
        TextView icon = findViewById(iconId);
        TextView label = findViewById(labelId);
        if (container == null || icon == null || label == null) return;
        boolean active = tab.equals(selected);
        container.setBackgroundResource(active ? R.drawable.nav_selected : android.R.color.transparent);
        icon.setTextColor(active ? GREEN : MUTED);
        label.setTextColor(active ? GREEN : MUTED);
        label.setTypeface(null, active ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
    }

    private void confirm(String title, String message, String positive, Runnable onConfirm) {
        View content = getLayoutInflater().inflate(R.layout.pt_confirm_dialog, null, false);
        ((TextView) content.findViewById(R.id.confirmation_title)).setText(title);
        ((TextView) content.findViewById(R.id.confirmation_message)).setText(message);
        new AlertDialog.Builder(this)
                .setView(content)
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setPositiveButton(positive, (dialog, which) -> onConfirm.run())
                .show();
    }

    private void configureEmptyState(int stubId, String title, String message) {
        View stubView = findViewById(stubId);
        if (!(stubView instanceof ViewStub)) return;
        ((ViewStub) stubView).setOnInflateListener((stub, inflated) -> {
            TextView titleView = inflated.findViewById(R.id.empty_state_title);
            TextView messageView = inflated.findViewById(R.id.empty_state_message);
            if (titleView != null) titleView.setText(title);
            if (messageView != null) messageView.setText(message);
        });
    }

    private int layoutFor(String screen) {
        switch (screen) {
            case "login": return R.layout.activity_main;
            case "pos": return R.layout.layout_pos;
            case "sale_complete": return R.layout.layout_sale_complete;
            case "inventory": return R.layout.layout_inventory;
            case "harvest": return R.layout.layout_harvest;
            case "adjustment": return R.layout.layout_adjustment;
            case "reports": return R.layout.layout_reports;
            case "shift_summary": return R.layout.layout_shift_summary;
            case "users": return R.layout.layout_users;
            case "pricing": return R.layout.layout_pricing;
            case "sync_status": return R.layout.layout_sync_status;
            case "more": return R.layout.layout_more;
            default: return R.layout.layout_dashboard;
        }
    }

    private String[] titleFor(String screen) {
        switch (screen) {
            case "pos": return new String[]{"New sale", "Local cash sale · Current open shift"};
            case "sale_complete": return new String[]{"Sale receipt", "Saved sale · This device"};
            case "inventory": return new String[]{"Egg inventory", "Maria Santos · Sales Personnel"};
            case "harvest": return new String[]{"Record harvest", "Elena Reyes · Farm Administrator"};
            case "adjustment": return new String[]{"Inventory adjustment", "Elena Reyes · System Administrator"};
            case "reports": return new String[]{"Reports", "Elena Reyes · System Administrator"};
            case "shift_summary": return new String[]{"My shift summary", "Maria Santos · Sales Personnel"};
            case "users": return new String[]{"User management", "Elena Reyes · System Administrator"};
            case "pricing": return new String[]{"Egg pricing", "Elena Reyes · System Administrator"};
            case "sync_status": return new String[]{"Sync status", "Elena Reyes · System Administrator"};
            case "more": return new String[]{"More", "Elena Reyes · System Administrator"};
            default: return new String[]{"PoultryTrack", "Maria Santos · Sales Personnel"};
        }
    }

    private void bind(int id, Runnable action) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> action.run());
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void goBack() {
        if (!backStack.isEmpty()) show(backStack.pop());
        else show("dashboard");
    }

}
