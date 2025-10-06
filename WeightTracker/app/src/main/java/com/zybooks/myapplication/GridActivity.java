package com.zybooks.myapplication;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zybooks.myapplication.models.GoalRecord;
import com.zybooks.myapplication.models.WeightRecord;
import com.zybooks.myapplication.net.Api;
import com.zybooks.myapplication.ui.WeightAdapter;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * GridActivity
 * - Shows a list of weight records (RecyclerView)
 * - Lets the user add a new weight (optionally with a picked date)
 * - Computes rolling average + trend and displays summary
 * - Checks server-stored goal after adding and sends a dummy SMS if reached
 */
public class GridActivity extends AppCompatActivity {

    // --- constants ---
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/uuuu");
    private static final DateTimeFormatter PRETTY_DATE = DateTimeFormatter.ofPattern("MMM d, uuuu");
    private static final String DUMMY_SMS_NUMBER = "1234567890";
    private static final int ROLLING_WINDOW = 7; // last N entries for rolling avg

    // --- views & state ---
    private Api api;
    private TextView tv;              // summary (rolling avg, trend, projection)
    private EditText input;           // weight input
    private EditText dateInput;       // date input (opens DatePicker)
    private RecyclerView rv;
    private WeightAdapter adapter;

    // Convert "M/d/yyyy" (local) to ISO Instant at start-of-day UTC (server expects ISO)
    private @androidx.annotation.Nullable String toIsoUtc(String mdy) {
        try {
            LocalDate d = LocalDate.parse(mdy, US_DATE);
            return d.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT);
        } catch (Exception ignore) {
            return null; // bad/empty input -> allow server to default to NOW
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_grid);

        // Edge-to-edge insets padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // --- find views ---
        tv        = findViewById(R.id.msg);
        input     = findViewById(R.id.weightInput);
        dateInput = findViewById(R.id.dateInput);
        Button send     = findViewById(R.id.sendButton);
        Button notifBtn = findViewById(R.id.notificationButton);
        rv = findViewById(R.id.weightsList);

        // Open notifications/goal screen
        notifBtn.setOnClickListener(v ->
                startActivity(new Intent(GridActivity.this, GoalNotificationActivity.class))
        );

        // Prefill date once on first creation (Android will restore after rotations)
        if (savedInstanceState == null) {
            dateInput.setText(LocalDate.now().format(US_DATE));
        }

        // Build API client (adds auth via interceptor)
        try {
            api = Api.get(this);
        } catch (Exception e) {
            tv.setText("Auth init error");
            return;
        }

        // --- RecyclerView setup ---
        adapter = new WeightAdapter(new WeightAdapter.OnItemAction() {
            @Override public void onEdit(WeightRecord r) {
                // Open detail screen with id, value, and original ISO timestamp
                Intent i = new Intent(GridActivity.this, DetailActivity.class);
                i.putExtra(DetailActivity.EXTRA_ID, r.getId());
                i.putExtra(DetailActivity.EXTRA_VALUE, r.getWeight());
                i.putExtra(DetailActivity.EXTRA_DATE_ISO, r.getDate()); // server "recorded_at" ISO string
                startActivity(i);
            }
            @Override public void onDelete(WeightRecord r) {
                tv.setText("Deleting…");
                api.deleteWeight(r.getId(), new Api.DeleteWeightCallback() {
                    @Override public void onSuccess(boolean deleted) {
                        runOnUiThread(() -> {
                            if (deleted) {
                                getWeights(); // refresh list + recompute analytics
                            } else {
                                tv.setText("Nothing deleted");
                            }
                        });
                    }
                    @Override public void onError(int code, String msg) {
                        runOnUiThread(() -> tv.setText("API " + code + ": " + msg));
                    }
                });
            }
        });
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // --- Date picker wiring ---
        dateInput.setOnClickListener(v -> {
            // Initialize picker from current text if parseable; else today
            LocalDate init = LocalDate.now();
            String cur = dateInput.getText().toString().trim();
            try { init = LocalDate.parse(cur, US_DATE); } catch (Exception ignored) {}

            DatePickerDialog dialog = new DatePickerDialog(
                    GridActivity.this,
                    (DatePicker view, int year, int month, int day) -> {
                        // month is 0-based in the picker
                        String dateStr = (month + 1) + "/" + day + "/" + year;
                        dateInput.setText(dateStr);
                    },
                    init.getYear(),
                    init.getMonthValue() - 1,
                    init.getDayOfMonth()
            );
            dialog.show();
        });

        // Kick off initial calls
        fetchHealth(); // will be overwritten by analytics summary when weights load
        getWeights();

        // Add a record when tapped
        send.setOnClickListener(v -> postWeightRecord());
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWeights(); // re-fetch to show latest values & recompute analytics
    }

    /// GET /health -> small one-line status (placeholder until weights summary loads)
    private void fetchHealth() {
        api.health(new Api.HealthCallback() {
            @Override public void onSuccess(boolean ok) {
                runOnUiThread(() -> tv.setText(ok ? "Connected" : "Health not OK"));
            }
            @Override public void onError(int code, String msg) {
                runOnUiThread(() -> tv.setText("API " + code + ": " + msg));
            }
        });
    }

    /// GET /weights -> bind list and compute analytics summary
    private void getWeights() {
        tv.setText("Loading weights…");

        // Show cached records immediately
        DatabaseHelper db = new DatabaseHelper(this);
        List<WeightRecord> cached = db.getAllWeights();
        // If the cache isn't empty, render the analytics
        if (!cached.isEmpty()) {
            adapter.submitList(cached, () -> rv.scrollToPosition(0));
            renderAnalyticsSummary(cached);
        }

        // Then try fetching fresh data from API
        api.listWeights(new Api.WeightsCallback() {
            @Override public void onSuccess(List<WeightRecord> items) {
                runOnUiThread(() -> {

                    // On success update the UI list and update the analytics
                    adapter.submitList(items, () -> rv.scrollToPosition(0));
                    renderAnalyticsSummary(items);
                    // Update cache with fresh copy
                    new Thread(() -> {
                        DatabaseHelper db2 = new DatabaseHelper(GridActivity.this);
                        db2.replaceWeights(items);
                    }).start();
                });
            }
            @Override public void onError(int code, String msg) {
                // Only show error if no cache existed
                if (cached.isEmpty()) {
                    runOnUiThread(() -> tv.setText("API " + code + ": " + msg));
                }
            }
        });
    }

    /// Build and display the analytics summary (rolling avg, trend, projection)
    private void renderAnalyticsSummary(List<WeightRecord> items) {
        // If there are no weight entries, skip analytics.
        if (items == null || items.isEmpty()) {
            tv.setText("No weights yet.");
            return;
        }

        // Rolling average of last N entries (assumes newest-first list)
        double avg = Analytics.lastNAverage(items, ROLLING_WINDOW);

        // Linear trend (lb/day) from least squares
        Analytics.Trend t = Analytics.linearTrend(items);
        String trendLine;
        String projectionLine = "Projection: —";

        if (t == null) {
            trendLine = "Trend: not enough data";
        } else {
            // Units here are lb/day
            double lbPerDay = t.slopeLBPerDay; //treat as lb/day
            double lbPerWeek = lbPerDay * 7.0;

            // Nicely formatted slope (show sign)
            String slopeStr = String.format("%+.3f lb/day (%+.2f lb/week)", lbPerDay, lbPerWeek);
            trendLine = "Trend: " + slopeStr;

            // If we have a downward trend, attempt a projection with the user's goal
            if (lbPerDay < 0) {
                api.getGoal(new Api.GetGoalCallback() {
                    @Override public void onSuccess(GoalRecord goal) {
                        // Update summary with projection if a goal exists
                        runOnUiThread(() -> {
                            String summaryTop = String.format(
                                    "7-entry avg: %.2f lb\n%s",
                                    avg, trendLine
                            );
                            if (goal != null && !Double.isNaN(goal.getValue())) {
                                // Project goal date from trend line
                                // Treat goal value as lbs (to match your UI)
                                java.time.LocalDate date = Analytics.projectGoalDate(t, goal.getValue());
                                String proj = (date == null)
                                        ? "Projection: no reliable date yet"
                                        : "Projection: ~" + date.format(PRETTY_DATE);
                                tv.setText(summaryTop + "\n" + proj);
                            } else {
                                tv.setText(summaryTop + "\nProjection: set a goal to see an estimate");
                            }
                        });
                    }
                    @Override public void onError(int code, String message) {
                        // If goal fetch fails, still show avg + trend
                        runOnUiThread(() -> tv.setText(String.format(
                                "7-entry avg: %.2f lb\n%s\nProjection: unavailable",
                                avg, trendLine
                        )));
                    }
                });
                return; // Update tv in the goal callback
            }
        }

        // No downward trend (or not enough data): show avg + trend, projection blank
        tv.setText(String.format(
                "7-entry avg: %.2f lb\n%s\n%s",
                avg, trendLine, projectionLine
        ));
    }

    /// POST /weights -> add record, refresh list, then check goal.
    private void postWeightRecord() {
        // Validate weight input
        String text = input.getText().toString().trim();
        final double value;
        try {
            value = Double.parseDouble(text);
        } catch (NumberFormatException e) {
            tv.setText("Enter a valid number (e.g. 182.5)");
            return;
        }

        // Optional picked date -> ISO
        String picked = dateInput.getText().toString().trim();
        String isoUtc = picked.isEmpty() ? null : toIsoUtc(picked);

        tv.setText("Saving…");
        api.createWeight(value, isoUtc, new Api.CreateWeightCallback() {
            @Override public void onSuccess(long id) {
                // Reset the UI, retrieve new weights, and check goal
                runOnUiThread(() -> {
                    input.setText("");
                    dateInput.setText(LocalDate.now().format(US_DATE)); // reset to today
                    getWeights();                 // triggers analytics summary refresh
                    checkGoalAndSendSMS(value);   // fire-and-forget
                });
            }
            @Override public void onError(int code, String msg) {
                // Inform the user an error occurred
                runOnUiThread(() -> tv.setText("API " + code + ": " + msg));
            }
        });
    }

    /// After adding a weight, GET /goal and, if met, send a dummy SMS.
    private void checkGoalAndSendSMS(double weight) {
        api.getGoal(new Api.GetGoalCallback() {
            @Override public void onSuccess(@androidx.annotation.Nullable GoalRecord goal) {
                if (goal == null) return; // no goal set
                double goalWeight = goal.getValue();
                if (!Double.isNaN(goalWeight) && weight <= goalWeight) {
                    runOnUiThread(() -> {
                        // Just inform the user if permission is missing (no prompt here)
                        if (ContextCompat.checkSelfPermission(
                                GridActivity.this, Manifest.permission.SEND_SMS)
                                == PackageManager.PERMISSION_GRANTED) {
                            // Try to send the SMS notification
                            try {
                                SmsManager.getDefault().sendTextMessage(
                                        DUMMY_SMS_NUMBER,
                                        null,
                                        "Congratulations, you hit your target goal of " + goalWeight + " lb!",
                                        null,
                                        null
                                );
                                Toast.makeText(GridActivity.this, "Goal reached! SMS sent.", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(GridActivity.this, "Failed to send SMS.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(GridActivity.this, "Goal reached, but SMS permission not granted", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
            @Override public void onError(int code, String msg) {
                // Provide the user a toast with an error message
                Toast.makeText(GridActivity.this, "Goal check failed", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
