package com.zybooks.myapplication;

import androidx.annotation.Nullable;

import com.zybooks.myapplication.models.WeightRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Analytics
 * - Rolling average (entry-count window)
 * - Linear trend via least squares (slope in lb/day)
 * - Goal date projection from linear trend
 *
 * All methods are O(n) over the number of records.
 */
public final class Analytics {

    private Analytics() {}

    /// Return the average of the last N entries (most recent first list works fine).
    public static double lastNAverage(List<WeightRecord> items, int window) {
        if (items == null || items.isEmpty() || window <= 0) return Double.NaN;
        int n = Math.min(window, items.size());
        double sum = 0.0;
        for (int i = 0; i < n; i++) sum += items.get(i).getWeight();
        return sum / n;
    }

    /// Rolling average series (window by entry count). Result is aligned to the same order as input.
    /// If input is newest-first, output is newest-first; same for oldest-first.
    public static List<Double> rollingAverageSeries(List<WeightRecord> items, int window) {
        List<Double> out = new ArrayList<>();
        if (items == null || items.isEmpty() || window <= 0) return out;

        double sum = 0.0;
        int n = items.size();
        for (int i = 0; i < n; i++) {
            sum += items.get(i).getWeight();
            if (i >= window) sum -= items.get(i - window).getWeight();
            out.add(i < window - 1 ? Double.NaN : sum / Math.min(window, i + 1));
        }
        return out;
    }

    /** Trend result: y = slope * x + intercept, where:
     *  - x is epochDay (days since 1970-01-01, UTC)
     *  - y is weight (kg)
     *  slope is kg/day (negative => losing weight)
     */
    public static final class Trend {
        public final double slopeLBPerDay;
        public final double intercept;
        public final long minEpochDay;
        public final long maxEpochDay;

        Trend(double m, double b, long minX, long maxX) {
            slopeLBPerDay = m; intercept = b; minEpochDay = minX; maxEpochDay = maxX;
        }
    }

    /// Compute linear regression over all points (least squares). Returns null if <2 points or degenerate.
    public static @Nullable Trend linearTrend(List<WeightRecord> items) {
        if (items == null || items.size() < 2) return null;

        // Regress y (weight) on x (epochDay) for stability using sums.
        double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
        int n = 0;
        long minX = Long.MAX_VALUE, maxX = Long.MIN_VALUE;

        // For each record, extract (x = epochDay, y = weight) and accumulate sums
        for (WeightRecord r : items) {

            // Get the date
            String iso = r.getDate();
            // skip rows with no timestamp
            if (iso == null || iso.isEmpty()) continue;
            long x = isoToEpochDay(iso); // epochDay makes time-of-day irrelevant

            // Get the weight
            double y = r.getWeight();
            // skip malformed weights
            if (Double.isNaN(y)) continue;

            sumX  += x;
            sumY  += y;
            sumXX += (double) x * x;
            sumXY += (double) x * y;
            n++;

            // Track min/max x to later ensure projections are in the future
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
        }
        if (n < 2) return null;

        // Denominator for slope
        double denom = (n * sumXX - sumX * sumX);
        if (Math.abs(denom) < 1e-9) return null;

        // m (lb/day) and b (lb at epochDay=0)
        double slope = (n * sumXY - sumX * sumY) / denom;
        double intercept = (sumY - slope * sumX) / n;

        return new Trend(slope, intercept, minX, maxX);
    }

    /// Given a trend and a goal weight, return the projected date the line will cross the goal.
    /// Returns null if slope is not downward (>= 0) or the solution is not in the future.
    public static @Nullable LocalDate projectGoalDate(Trend t, double goalKg) {
        if (t == null) return null;
        if (t.slopeLBPerDay >= -1e-9) return null; // not trending down

        // Solve x_goal = (goal - b)/m  (m is negative)
        double xGoal = (goalKg - t.intercept) / t.slopeLBPerDay;
        long xRounded = Math.round(xGoal);

        // Only meaningful if after the latest data point
        if (xRounded <= t.maxEpochDay) return null;

        return LocalDate.ofEpochDay(xRounded);
    }

    // -------- helpers --------

    private static long isoToEpochDay(String iso) {
        return Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
    }
}