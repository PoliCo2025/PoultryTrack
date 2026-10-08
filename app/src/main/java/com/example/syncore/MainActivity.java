package com.example.syncore;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewStub;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayDeque;
import java.util.Deque;

/** Routes between PoultryTrack's XML-defined screens and their visual states. */
public class MainActivity extends AppCompatActivity {
    private static final int GREEN = Color.rgb(40, 93, 59);
    private static final int MUTED = Color.rgb(100, 112, 103);
    private String current = "login";
    private final Deque<String> backStack = new ArrayDeque<>();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(244, 246, 242));
        getWindow().setNavigationBarColor(Color.rgb(244, 246, 242));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        show("login");
    }

    private void navigate(String screen) {
        if (!screen.equals(current)) backStack.push(current);
        show(screen);
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
        bind(R.id.complete_sale, () -> confirm("Complete this sale?",
                "Review the cash sale details before continuing to the receipt.",
                "Complete sale", () -> navigate("sale_complete")));
        bind(R.id.start_sale, () -> navigate("pos"));
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
            case "pos": return new String[]{"New sale", "Maria Santos · Sales Personnel"};
            case "sale_complete": return new String[]{"Sale completed", "Sales Personnel · Local record"};
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

    @Override public void onBackPressed() {
        if (!backStack.isEmpty()) goBack();
        else if (current.equals("login")) super.onBackPressed();
        else if (!current.equals("dashboard")) show("dashboard");
        else super.onBackPressed();
    }
}
