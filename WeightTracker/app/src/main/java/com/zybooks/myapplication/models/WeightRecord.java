package com.zybooks.myapplication.models;

import org.json.JSONObject;

/// WeightRecord - represents a single weight row from the API.
public class WeightRecord {
    private long id;
    private double value; // weight value
    private String date;

    // Constructor for a basic weight record
    public WeightRecord(long id, double weight, String date) {
        this.id = id;
        this.value = weight;
        this.date = date;
    }

    // Parse one row from the API (fields: id, value, recorded_at)
    public static WeightRecord fromJson(JSONObject o) {
        long id = o.optLong("id", -1);
        double v = o.optDouble("value", Double.NaN);
        String at = o.optString("recorded_at", "");
        return new WeightRecord(id, v, at);
    }

    // Getters for the members
    public long getId() { return id; }
    public double getWeight() { return value; }
    public String getDate() { return date; }
}