package com.zybooks.myapplication.net;

import android.content.Context;

import com.zybooks.myapplication.models.GoalRecord;
import com.zybooks.myapplication.models.WeightRecord;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Api
 * - Centralizes all HTTP calls to the backend
 * - Small typed callbacks per endpoint
 * - Single OkHttp client with auth interceptor
 */
public final class Api {

    /// Base URL for all requests (change once, used everywhere)
    public static final String BASE = "https://api.10-0-2-2.sslip.io";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static volatile Api INSTANCE;
    private final OkHttpClient http;

    private Api(Context appCtx) throws Exception {
        /// Interceptor adds/refreshes auth on each call
        AuthInterceptor authi = new AuthInterceptor(appCtx);
        this.http = new OkHttpClient.Builder().addInterceptor(authi).build();
    }

    /// Singleton bound to the app context
    public static Api get(Context ctx) throws Exception {
        if (INSTANCE == null) {
            synchronized (Api.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Api(ctx.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    // -------------------------
    // Low-level JSON helpers
    // -------------------------

    private interface JsonCallback {
        void onSuccess(JSONObject json);
        void onError(int code, String message);
    }

    /// GET helper: enqueue request, map to JSON or error
    private void getJson(HttpUrl url, JsonCallback cb) {
        // Build a simple GET request
        Request req = new Request.Builder().url(url).get().build();

        // Execute asynchronously
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                // Network/IO failure (no HTTP status)
                cb.onError(0, e.getMessage());
            }
            @Override public void onResponse(Call call, Response res) throws IOException {
                // Read body once (success or error text)
                String body = res.body() != null ? res.body().string() : "";

                // Forward HTTP errors to caller with status code + body text
                if (!res.isSuccessful()) { cb.onError(res.code(), body); return; }

                // Try to parse JSON; surface a parse error if it fails
                try { cb.onSuccess(new JSONObject(body)); }
                catch (JSONException ex) { cb.onError(res.code(), "Parse error: " + ex.getMessage()); }
            }
        });
    }

    /// POST helper: send JSON body, expect JSON back
    private void postJson(String path, JSONObject payload, JsonCallback cb) {
        // Build a POST with JSON body
        Request req = new Request.Builder()
                .url(BASE + path)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        // Execute asynchronously
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { cb.onError(0, e.getMessage()); }
            @Override public void onResponse(Call call, Response res) throws IOException {
                String body = res.body() != null ? res.body().string() : "";

                // Forward HTTP errors
                if (!res.isSuccessful()) { cb.onError(res.code(), body); return; }

                // Parse JSON result
                try { cb.onSuccess(new JSONObject(body)); }
                catch (JSONException ex) { cb.onError(res.code(), "Parse error: " + ex.getMessage()); }
            }
        });
    }

    /// PUT helper: send JSON body, expect JSON back
    private void putJson(String path, JSONObject payload, JsonCallback cb) {
        // Build a PUT with JSON body
        Request req = new Request.Builder()
                .url(BASE + path)
                .put(RequestBody.create(payload.toString(), JSON))
                .build();

        // Execute asynchronously
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { cb.onError(0, e.getMessage()); }
            @Override public void onResponse(Call call, Response res) throws IOException {
                String body = res.body() != null ? res.body().string() : "";

                // Forward HTTP errors
                if (!res.isSuccessful()) { cb.onError(res.code(), body); return; }

                // Parse JSON result
                try { cb.onSuccess(new JSONObject(body)); }
                catch (JSONException ex) { cb.onError(res.code(), "Parse error: " + ex.getMessage()); }
            }
        });
    }

    // -------------------------
    // Public endpoints
    // -------------------------

    /// GET /health - returns a simple “ok” flag
    public interface HealthCallback {
        void onSuccess(boolean ok);
        void onError(int code, String message);
    }
    public void health(HealthCallback cb) {
        // Call /health and project to a boolean for the UI
        getJson(HttpUrl.parse(BASE + "/health"), new JsonCallback() {
            @Override public void onSuccess(JSONObject json) { cb.onSuccess(json.optBoolean("ok", false)); }
            @Override public void onError(int code, String message) { cb.onError(code, message); }
        });
    }

    /// POST /weights - create a weight entry (optional timestamp)
    /// If no timestamp is provided, the server uses “now”.
    public interface CreateWeightCallback {
        void onSuccess(long id);
        void onError(int code, String message);
    }
    public void createWeight(double value, @androidx.annotation.Nullable String isoRecordedAtUtc,
                             CreateWeightCallback cb) {
        // Build payload with required value and optional ISO timestamp
        JSONObject payload = new JSONObject();
        try {
            payload.put("value", value);
            if (isoRecordedAtUtc != null && !isoRecordedAtUtc.isEmpty()) {
                payload.put("recorded_at", isoRecordedAtUtc);
            }
        } catch (JSONException e) {
            // Fail fast if we couldn't construct JSON
            cb.onError(0, "Build JSON error: " + e.getMessage());
            return;
        }

        // POST and return the new row id to the caller
        postJson("/weights", payload, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) { cb.onSuccess(json.optLong("id", -1)); }
            @Override public void onError(int code, String message) { cb.onError(code, message); }
        });
    }
    public void createWeight(double value, CreateWeightCallback cb) {
        // Overload without a timestamp (server will set it)
        createWeight(value, null, cb);
    }

    /// GET /weights?limit&offset - paged list of recent weights
    /// “limit” = how many to return. “offset” = how many newest items to skip.
    public interface WeightsCallback {
        void onSuccess(List<WeightRecord> items);
        void onError(int code, String message);
    }
    public void listWeights(int limit, int offset, WeightsCallback cb) {
        // Build URL with paging params
        HttpUrl url = HttpUrl.parse(BASE + "/weights").newBuilder()
                .addQueryParameter("limit", String.valueOf(limit))
                .addQueryParameter("offset", String.valueOf(offset))
                .build();

        // GET, then map "items" into a typed list
        getJson(url, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) {
                // Extract array and convert each object via WeightRecord.fromJson
                JSONArray arr = json.optJSONArray("items");
                List<WeightRecord> out = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject row = arr.optJSONObject(i);
                        if (row != null) out.add(WeightRecord.fromJson(row));
                    }
                }
                // Return parsed list to the UI layer
                cb.onSuccess(out);
            }
            @Override public void onError(int code, String message) { cb.onError(code, message); }
        });
    }
    /// Convenience: first page
    public void listWeights(WeightsCallback cb) { listWeights(100, 0, cb); }

    /// PUT /weights/:id - partial update (value and/or recorded_at)
    /// Returns true if a row was actually updated (matches id + ownership).
    public interface UpdateWeightCallback {
        void onSuccess(boolean updated);
        void onError(int code, String message);
    }

    public void updateWeight(
            long id,
            @androidx.annotation.Nullable Double newValue,
            @androidx.annotation.Nullable String isoRecordedAtUtc,
            UpdateWeightCallback cb
    ) {
        // Build a payload only with provided fields
        final JSONObject payload = new JSONObject();
        boolean hasAny = false;

        try {
            if (newValue != null) {
                payload.put("value", newValue);
                hasAny = true;
            }
            if (isoRecordedAtUtc != null && !isoRecordedAtUtc.isEmpty()) {
                payload.put("recorded_at", isoRecordedAtUtc);
                hasAny = true;
            }
        } catch (JSONException e) {
            cb.onError(0, "Build JSON error: " + e.getMessage());
            return;
        }

        // Require at least one field to update
        if (!hasAny) {
            cb.onError(0, "Nothing to update");
            return;
        }

        // Send PUT and interpret the server’s updated-count as a boolean
        putJson("/weights/" + id, payload, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) {
                cb.onSuccess(json.optInt("updated", 0) > 0);
            }
            @Override public void onError(int code, String message) {
                cb.onError(code, message);
            }
        });
    }

    /// Convenience: update only the value
    public void updateWeightValue(long id, double newValue, UpdateWeightCallback cb) {
        updateWeight(id, Double.valueOf(newValue), null, cb);
    }

    /// Convenience: update only the timestamp (ISO-8601 in UTC)
    public void updateWeightTimestamp(long id, String isoRecordedAtUtc, UpdateWeightCallback cb) {
        updateWeight(id, null, isoRecordedAtUtc, cb);
    }

    /// DELETE /weights/:id - removes a weight if it belongs to the user
    /// Callback gets true if a row was actually deleted.
    public interface DeleteWeightCallback {
        void onSuccess(boolean deleted);
        void onError(int code, String message);
    }
    public void deleteWeight(long id, DeleteWeightCallback cb) {
        // Build a DELETE request to the row resource
        Request req = new Request.Builder()
                .url(BASE + "/weights/" + id)
                .delete()
                .build();

        // Execute and convert the “deleted” count to a boolean
        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                cb.onError(0, e.getMessage());
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response res) throws java.io.IOException {
                // Read once, check status, then parse
                String body = res.body() != null ? res.body().string() : "";
                if (!res.isSuccessful()) { cb.onError(res.code(), body); return; }

                try {
                    org.json.JSONObject j = new org.json.JSONObject(body);
                    // deleted>0 → a row was removed; 0 → nothing matched
                    cb.onSuccess(j.optInt("deleted", 0) > 0);
                } catch (org.json.JSONException ex) {
                    cb.onError(res.code(), "Parse error: " + ex.getMessage());
                }
            }
        });
    }

    /// GET /goal - fetches the user’s goal (or null if none set)
    public interface GetGoalCallback {
        void onSuccess(@androidx.annotation.Nullable GoalRecord goal);
        void onError(int code, String message);
    }
    public void getGoal(GetGoalCallback cb) {
        // GET goal and map: missing → null, present → GoalRecord
        getJson(HttpUrl.parse(BASE + "/goal"), new JsonCallback() {
            @Override public void onSuccess(JSONObject json) {
                JSONObject g = json.optJSONObject("goal");
                if (g == null || g == JSONObject.NULL) {
                    cb.onSuccess(null);
                } else {
                    cb.onSuccess(GoalRecord.fromJson(g));
                }
            }
            @Override public void onError(int code, String message) { cb.onError(code, message); }
        });
    }

    /// PUT /goal - creates or updates the user’s goal (optional timestamp)
    /// If no timestamp is provided, the server sets it.
    public interface PutGoalCallback {
        void onSuccess();
        void onError(int code, String message);
    }
    public void putGoal(double value,
                        @androidx.annotation.Nullable String isoAt,
                        PutGoalCallback cb) {
        // Build payload with required value and optional ISO timestamp
        JSONObject payload = new JSONObject();
        try {
            payload.put("value", value);
            if (isoAt != null && !isoAt.isEmpty()) {
                payload.put("at", isoAt);
            }
        } catch (JSONException e) {
            cb.onError(0, "Build JSON error: " + e.getMessage());
            return;
        }

        // PUT and just confirm success (caller doesn’t need a body)
        putJson("/goal", payload, new JsonCallback() {
            @Override public void onSuccess(JSONObject json) { cb.onSuccess(); }
            @Override public void onError(int code, String message) { cb.onError(code, message); }
        });
    }
    public void putGoal(double value, PutGoalCallback cb) { putGoal(value, null, cb); }

    /// DELETE /goal - removes the user’s goal if it exists
    /// Callback gets true if a goal was actually deleted.
    public interface DeleteGoalCallback {
        void onSuccess(boolean deleted);
        void onError(int code, String message);
    }
    public void deleteGoal(DeleteGoalCallback cb) {
        // Build a DELETE request to the singleton goal resource
        Request req = new Request.Builder().url(BASE + "/goal").delete().build();

        // Execute and convert “deleted” count to boolean
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) { cb.onError(0, e.getMessage()); }
            @Override public void onResponse(Call call, Response res) throws IOException {
                // Read once, check status, then parse
                String body = res.body() != null ? res.body().string() : "";
                if (!res.isSuccessful()) { cb.onError(res.code(), body); return; }

                try {
                    JSONObject j = new JSONObject(body);
                    cb.onSuccess(j.optInt("deleted", 0) > 0);
                } catch (JSONException ex) {
                    cb.onError(res.code(), "Parse error: " + ex.getMessage());
                }
            }
        });
    }
}
