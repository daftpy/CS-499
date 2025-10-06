/*
 * Local cache for offline-first use.
 * Stores a mirror of server data (weights + single goal).
 */
package com.zybooks.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.zybooks.myapplication.models.GoalRecord;
import com.zybooks.myapplication.models.WeightRecord;

import java.util.ArrayList;
import java.util.List;


/**
 * DatabaseHelper
 * - Provides offline cache access using a local
 * - SQLite database.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "weighttrack_cache.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_WEIGHT = "weight";
    private static final String TABLE_GOAL   = "goal";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create weights table (server id is the PK).
        db.execSQL("CREATE TABLE " + TABLE_WEIGHT + " (" +
                "id INTEGER PRIMARY KEY," +
                "value REAL NOT NULL," +
                "recorded_at TEXT NOT NULL)");

        // Create single-row goal table (replaced on fetch).
        db.execSQL("CREATE TABLE " + TABLE_GOAL + " (" +
                "id INTEGER PRIMARY KEY," +
                "value REAL NOT NULL," +
                "recorded_at TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Pure cache: drop + recreate to match latest schema.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_WEIGHT);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_GOAL);
        onCreate(db);
    }

    // ---------------- Weights ----------------

    /// Replace entire weight cache with the latest server list.
    public void replaceWeights(List<WeightRecord> items) {
        SQLiteDatabase db = getWritableDatabase();
        // batch writes atomically
        db.beginTransaction();
        try {
            // Clear old cache
            db.delete(TABLE_WEIGHT, null, null);
            for (WeightRecord r : items) {
                ContentValues v = new ContentValues();
                v.put("id", r.getId());                  // server id
                v.put("value", r.getWeight());           // value
                v.put("recorded_at", r.getDate());       // time

                // insert row
                db.insert(TABLE_WEIGHT, null, v);
            }
            // commit
            db.setTransactionSuccessful();
        } finally {
            // rollback if needed
            db.endTransaction();
            // free handle
            db.close();
        }
    }

    /// Return all cached weights, newest first.
    public List<WeightRecord> getAllWeights() {
        List<WeightRecord> out = new ArrayList<>();
        // open read-only
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(
                TABLE_WEIGHT,
                new String[]{"id", "value", "recorded_at"},
                null, null, null, null,
                "recorded_at DESC, id DESC"              // newest-first
        );
        // Iterate rows → model objects
        while (c.moveToNext()) {
            long id    = c.getLong(c.getColumnIndexOrThrow("id"));          // server id
            double val = c.getDouble(c.getColumnIndexOrThrow("value"));     // weight
            String at  = c.getString(c.getColumnIndexOrThrow("recorded_at"));// timestamp
            out.add(new WeightRecord(id, val, at));
        }
        // close cursor and connection
        c.close();
        db.close();
        return out;
    }

    // ---------------- Goal ----------------

    /// Replace the single cached goal with the latest from server.
    public void setGoal(GoalRecord g) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_GOAL, null, null);  // keep only one row
        ContentValues v = new ContentValues();
        v.put("id", 1);                                  // static singleton key
        v.put("value", g.getValue());                    // target weight
        v.put("recorded_at", g.getDate());               // server timestamp
        db.insert(TABLE_GOAL, null, v);    // insert new goal
        db.close();
    }

    /// Return cached goal or null if none stored.
    public GoalRecord getGoal() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(
                TABLE_GOAL,
                new String[]{"value", "recorded_at"},
                null, null, null, null, null
        );
        GoalRecord g = null;
        if (c.moveToFirst()) {
            double val = c.getDouble(c.getColumnIndexOrThrow("value"));     // goal
            String at  = c.getString(c.getColumnIndexOrThrow("recorded_at"));// timestamp
            g = new GoalRecord(val, at);
        }
        c.close();                                       // cleanup
        db.close();
        return g;
    }
}
