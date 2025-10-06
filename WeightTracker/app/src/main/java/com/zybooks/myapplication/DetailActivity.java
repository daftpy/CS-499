package com.zybooks.myapplication;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zybooks.myapplication.net.Api;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * DetailActivity
 * - Shows the details of a specific weight record
 * - Lets the user update the values of a specific record
 */
public class DetailActivity extends AppCompatActivity {

    /// Intent extras used by GridActivity
    public static final String EXTRA_ID       = "extra_weight_id";
    public static final String EXTRA_VALUE    = "extra_weight_value";
    public static final String EXTRA_DATE_ISO = "extra_weight_date_iso";

    /// Shared date formats
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("M/d/uuuu");

    // Inputs
    private EditText weightInput;
    private EditText dateInput;
    private Button updateButton;

    // Record identity
    private long recordId = -1L;

    // API
    private Api api;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // --- find views ---
        weightInput  = findViewById(R.id.editRecordWeightInput);
        dateInput    = findViewById(R.id.editRecordDateInput);
        updateButton = findViewById(R.id.updateRecordButton);

        // --- get API ---
        try {
            api = Api.get(this);
        } catch (Exception e) {
            Toast.makeText(this, "Auth init error", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // --- read extras and prefill ---
        recordId = getIntent().getLongExtra(EXTRA_ID, -1L);
        double value = getIntent().getDoubleExtra(EXTRA_VALUE, Double.NaN);
        String iso   = getIntent().getStringExtra(EXTRA_DATE_ISO);

        if (recordId <= 0) {
            Toast.makeText(this, "Missing record id", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!Double.isNaN(value)) {
            // Show raw number (you can format if you prefer)
            weightInput.setText(String.valueOf(value));
        }
        // Convert ISO → M/d/yyyy for editing
        String usDate = isoToUsDate(iso);
        if (!usDate.isEmpty()) {
            dateInput.setText(usDate);
        }

        // --- date picker wiring ---
        dateInput.setOnClickListener(v -> {
            LocalDate init = LocalDate.now();
            try {
                String cur = dateInput.getText().toString().trim();
                if (!cur.isEmpty()) init = LocalDate.parse(cur, US_DATE);
            } catch (Exception ignored) {}

            DatePickerDialog dlg = new DatePickerDialog(
                    DetailActivity.this,
                    (DatePicker view, int year, int month, int day) ->
                            dateInput.setText((month + 1) + "/" + day + "/" + year),
                    init.getYear(),
                    init.getMonthValue() - 1,
                    init.getDayOfMonth()
            );
            dlg.show();
        });

        // --- update button ---
        updateButton.setOnClickListener(v -> {
            // Collect updated fields; only send what’s provided
            Double newValue = parseDoubleOrNull(weightInput.getText().toString().trim());
            String newIso   = usToIsoOrNull(dateInput.getText().toString().trim());

            // Guard: nothing to update
            if (newValue == null && newIso == null) {
                Toast.makeText(this, "Nothing to update", Toast.LENGTH_SHORT).show();
                return;
            }

            // Call API (partial update)
            updateButton.setEnabled(false);
            api.updateWeight(recordId, newValue, newIso, new Api.UpdateWeightCallback() {
                @Override public void onSuccess(boolean updated) {
                    runOnUiThread(() -> {
                        updateButton.setEnabled(true);
                        Toast.makeText(DetailActivity.this,
                                updated ? "Updated ✔" : "No changes applied",
                                Toast.LENGTH_SHORT).show();
                        if (updated) finish(); // close and return to list
                    });
                }
                @Override public void onError(int code, String message) {
                    runOnUiThread(() -> {
                        updateButton.setEnabled(true);
                        Toast.makeText(DetailActivity.this,
                                "Update failed: " + (code == 0 ? message : code),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });
    }

    /// ISO/UTC → "M/d/yyyy"
    private String isoToUsDate(@Nullable String iso) {
        try {
            if (iso == null || iso.isEmpty()) return "";
            LocalDate d = Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate();
            return d.format(US_DATE);
        } catch (Exception e) {
            return "";
        }
    }

    /// "M/d/yyyy" → ISO/UTC, or null if invalid/empty
    private @Nullable String usToIsoOrNull(String mdy) {
        try {
            if (mdy == null || mdy.isEmpty()) return null;
            LocalDate d = LocalDate.parse(mdy, US_DATE);
            return d.atStartOfDay(ZoneOffset.UTC).format(java.time.format.DateTimeFormatter.ISO_INSTANT);
        } catch (Exception e) {
            return null;
        }
    }

    private @Nullable Double parseDoubleOrNull(String t) {
        try { return t.isEmpty() ? null : Double.valueOf(Double.parseDouble(t)); }
        catch (NumberFormatException e) { return null; }
    }
}
