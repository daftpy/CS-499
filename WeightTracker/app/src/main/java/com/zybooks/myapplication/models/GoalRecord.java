package com.zybooks.myapplication.models;

import org.json.JSONObject;

/// GoalRecord - represents a users target weight goal. Returned by the API
public class GoalRecord {
    private final double value;
    private final String date; // server "recorded_at"

    public GoalRecord(double value, String date) {
        this.value = value;
        this.date = date;
    }

    // Parse one row from the API (value, recorded_at)
    public static GoalRecord fromJson(JSONObject o) {
        double v = o.optDouble("value", Double.NaN);
        String at = o.optString("recorded_at", null);
        return new GoalRecord(v, at);
    }

    // Getters for the members
    public double getValue() { return value; }
    public String getDate()  { return date; }
}
