package com.zybooks.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zybooks.myapplication.models.GoalRecord;
import com.zybooks.myapplication.net.Api;

import java.util.Locale;

/**
 * Screen for enabling/disabling goal notifications and setting a goal weight.
 * - Toggle ON: requests SMS permission (once) and enables the input.
 * - Toggle OFF: clears goal on server and disables the input.
 * - Update button: PUT /goal with the entered value when toggle is ON.
 */
public class GoalNotificationActivity extends AppCompatActivity {

    // --- constants ---
    private static final int REQ_SEND_SMS = 1001;

    // --- UI ---
    private ToggleButton toggleButton;
    private EditText goalInput;
    private Button updateGoalButton;

    // --- api ---
    private Api api;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal_notification);

        // Bind views
        toggleButton = findViewById(R.id.toggleButton);
        goalInput = findViewById(R.id.goalInput);
        updateGoalButton = findViewById(R.id.updateGoalButton);

        // Build API client
        try {
            api = Api.get(this);
        } catch (Exception e) {
            toast("Auth init error");
            finish();
            return;
        }

        // Prefill UI from server
        loadGoal();

        // Toggle: enable/disable input and request permission when turning ON
        toggleButton.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                if (!hasSmsPermission()) {
                    // Ask once; result handled in onRequestPermissionsResult
                    ActivityCompat.requestPermissions(
                            GoalNotificationActivity.this,
                            new String[]{Manifest.permission.SEND_SMS},
                            REQ_SEND_SMS
                    );
                } else {
                    toast("SMS permission granted");
                }
                goalInput.setEnabled(true);
            } else {
                // Treat toggle OFF as "clear goal"
                goalInput.setEnabled(false);
                deleteGoal();
            }
        });

        // PUT /goal when clicked (only meaningful if toggle is ON)
        updateGoalButton.setOnClickListener(v -> {
            if (!toggleButton.isChecked()) {
                deleteGoal();
                return;
            }
            final String txt = goalInput.getText().toString().trim();
            if (txt.isEmpty()) { toast("Enter a goal weight (e.g. 75.0)"); return; }

            final double value;
            try {
                value = Double.parseDouble(txt);
            } catch (NumberFormatException ex) {
                toast("Invalid number");
                return;
            }
            putGoal(value);
        });
    }

    // ---------------------------
    // Permission helpers
    // ---------------------------

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SEND_SMS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                toast("SMS permission granted");
                // leave toggle ON, keep input enabled
                goalInput.setEnabled(true);
            } else {
                toast("SMS permission denied");
                // revert toggle and disable input to reflect no notifications
                toggleButton.setChecked(false);
                goalInput.setEnabled(false);
            }
        }
    }

    // ---------------------------
    // API calls
    // ---------------------------

    /// GET /goal and prefill UI.
    private void loadGoal() {
        setBusy(true);
        api.getGoal(new Api.GetGoalCallback() {
            @Override public void onSuccess(@Nullable GoalRecord goal) {
                // Initiate the UI and prefill the form
                runOnUiThread(() -> {
                    final boolean hasGoal = goal != null;
                    toggleButton.setChecked(hasGoal);
                    goalInput.setEnabled(hasGoal);
                    goalInput.setText(hasGoal
                            ? String.format(Locale.US, "%.2f", goal.getValue())
                            : "");
                    setBusy(false);
                });
            }
            @Override public void onError(int code, String message) {
                // Inform the user of an error
                runOnUiThread(() -> {
                    toast("Load goal failed");
                    toggleButton.setChecked(false);
                    goalInput.setEnabled(false);
                    setBusy(false);
                });
            }
        });
    }

    /// PUT /goal value.
    private void putGoal(double value) {
        setBusy(true);
        api.putGoal(value, null, new Api.PutGoalCallback() {
            @Override public void onSuccess() {
                // Reset the UI
                runOnUiThread(() -> {
                    toast("Goal updated");
                    toggleButton.setChecked(true);
                    goalInput.setEnabled(true);
                    setBusy(false);
                });
            }
            @Override public void onError(int code, String message) {
                // Inform the user of an error
                runOnUiThread(() -> {
                    toast("Update failed");
                    setBusy(false);
                });
            }
        });
    }

    /// DELETE /goal and reset UI.
    private void deleteGoal() {
        setBusy(true);
        api.deleteGoal(new Api.DeleteGoalCallback() {
            @Override public void onSuccess(boolean deleted) {
                // Reset the UI
                runOnUiThread(() -> {
                    toast(deleted ? "Goal cleared" : "No goal to clear");
                    toggleButton.setChecked(false);
                    goalInput.setText("");
                    goalInput.setEnabled(false);
                    setBusy(false);
                });
            }
            @Override public void onError(int code, String message) {
                // Prompt the user of an error
                runOnUiThread(() -> {
                    toast("Delete failed");
                    setBusy(false);
                });
            }
        });
    }

    // ---------------------------
    // UI utilities
    // ---------------------------

    /// Disable controls while network is in-flight.
    private void setBusy(boolean busy) {
        updateGoalButton.setEnabled(!busy);
        toggleButton.setEnabled(!busy);
        goalInput.setEnabled(!busy && toggleButton.isChecked());
    }

    ///  Informs the user of some message in a temporary toast
    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
