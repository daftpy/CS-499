import express from "express";
import cors from "cors";
import { requireAuth } from "./auth.js";
import { 
  deleteWeight,
  insertWeight,
  updateWeight,
  listWeights,
  upsertGoal,
  getGoal,
  deleteGoal,
  makePool,
  migrate
} from "./db.js";

const app = express();
app.use(express.json());
app.use(cors());

let pool; // shared DB pool (initialized at bootstrap)

/// GET /health - simple liveness probe (no auth)
app.get("/health", (req, res) => {
  console.log("Authorization:", req.headers.authorization || "(none)");
  res.json({ ok: true });
});

/// POST /test - echo endpoint to verify auth round-trip
app.post("/test", requireAuth, (req, res) => {
  const input = (req.body?.input ?? "").toString();
  res.json({
    ok: true,
    received: input,
    sub: req.user.sub,
    username: req.user.preferred_username,
    scope: req.user.scope,
  });
});

/// POST /weights - create a weight row
/// value is required and must be a finite number
/// recorded_at is optional and may be provided as "recorded_at" or "at"
app.post("/weights", requireAuth, async (req, res) => {
  try {
    // Validate and coerce the weight value
    const value = Number(req.body?.value);
    if (!Number.isFinite(value)) {
      return res.status(400).json({ ok: false, error: "Invalid value" });
    }

    // accept either "recorded_at" or short "at"
    const recordedAtRaw = req.body?.recorded_at ?? req.body?.at ?? null;

    // Insert and return the new id
    let id;
    try {
      id = await insertWeight(pool, req.user.sub, value, recordedAtRaw);
    } catch (e) {
      // Translate bad date into a 400 for the client
      if (/Invalid date/.test(String(e?.message))) {
        return res.status(400).json({ ok: false, error: "Invalid recorded_at" });
      }
      throw e;
    }

    res.json({ ok: true, id });
  } catch (err) {
    console.error("Insert failed:", err);
    res.status(500).json({ ok: false, error: "DB insert failed" });
  }
});

/// DELETE /weights/:id - delete one weight row owned by the caller
/// returns the number of rows deleted
app.delete("/weights/:id", requireAuth, async (req, res) => {
  // Validate path id
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ ok:false, error:"Invalid id" });
  }
  // Attempt delete scoped to the caller’s subject
  const affectedRows = await deleteWeight(pool, id, req.user.sub);
  res.json({ ok: true, deleted: affectedRows });
});

/// GET /weights - paginated list of the caller’s weights
/// limit caps the page size
/// offset skips that many newest records
app.get("/weights", requireAuth, async (req, res) => {
  try {
    // Parse paging with safe bounds
    const limit = Math.min(Number(req.query.limit) || 100, 500);
    const offset = Number(req.query.offset) || 0;

    // Query and return items in newest-first order
    const items = await listWeights(pool, req.user.sub, limit, offset);
    res.json({ ok: true, items });
  } catch (err) {
    console.error("Query failed:", err);
    res.status(500).json({ ok: false, error: "DB query failed" });
  }
});

/// PUT /weights/:id - partial update of a weight row
/// You may send value and recorded_at (or "at")
/// Only fields present are updated; 0 updates means no match or no change
app.put("/weights/:id", requireAuth, async (req, res) => {
  // Validate path id
  const id = Number(req.params.id);
  if (!Number.isInteger(id) || id <= 0) {
    return res.status(400).json({ ok: false, error: "Invalid id" });
  }

  // Detect presence (so 0 is allowed). We only update fields that are present.
  const hasValue = Object.prototype.hasOwnProperty.call(req.body, "value");
  const hasAt =
    Object.prototype.hasOwnProperty.call(req.body, "recorded_at") ||
    Object.prototype.hasOwnProperty.call(req.body, "at");

  if (!hasValue && !hasAt) {
    return res.status(400).json({ ok: false, error: "Nothing to update" });
  }

  // Validate value if provided
  let newValue;
  if (hasValue) {
    newValue = Number(req.body.value);
    if (!Number.isFinite(newValue)) {
      return res.status(400).json({ ok: false, error: "Invalid value" });
    }
  }

  // Accept either "recorded_at" or short "at" if provided
  const recordedAtRaw = hasAt ? (req.body?.recorded_at ?? req.body?.at) : undefined;

  try {
    // updated is the number of rows modified (0 or 1 in this case)
    const updated = await updateWeight(pool, id, req.user.sub, newValue, recordedAtRaw);
    res.json({ ok: true, updated }); // updated = 0 (no match) or 1 (updated)
  } catch (e) {
    // Map date conversion error to a 400
    if (/Invalid date/.test(String(e?.message))) {
      return res.status(400).json({ ok: false, error: "Invalid recorded_at" });
    }
    console.error("Update failed:", e);
    res.status(500).json({ ok: false, error: "DB update failed" });
  }
});

/// GET /goal - fetch the caller’s goal (null if none)
app.get("/goal", requireAuth, async (req, res) => {
  const row = await getGoal(pool, req.user.sub);
  res.json({ ok: true, goal: row });
});

/// PUT /goal - create or replace the caller’s goal
/// value is required; at is optional and must be a valid timestamp if provided
app.put("/goal", requireAuth, async (req, res) => {
  // Validate goal value
  const value = Number(req.body?.value);
  if (!Number.isFinite(value)) return res.status(400).json({ ok:false, error:"Invalid value" });

  // Optional client-supplied timestamp
  const at = req.body?.at ?? null;
  
  try {
    await upsertGoal(pool, req.user.sub, value, at);
    res.json({ ok: true });
  } catch (e) {
    if (/Invalid date/.test(String(e?.message))) {
      return res.status(400).json({ ok:false, error:"Invalid at timestamp" });
    }
    throw e;
  }
});

/// DELETE /goal - remove the caller’s goal if it exists
app.delete("/goal", requireAuth, async (req, res) => {
  const n = await deleteGoal(pool, req.user.sub);
  res.json({ ok: true, deleted: n });
});

// Bootstrap: connect + migrate, then listen
const PORT = process.env.PORT || 3000;

(async () => {
  try {
    // Create the pool once and run lightweight migrations
    pool = await makePool();
    await migrate(pool);
    app.locals.pool = pool;

    app.listen(PORT, () => {
      console.log(`API listening on :${PORT}`);
    });
  } catch (err) {
    console.error("Failed to start API:", err);
    process.exit(1);
  }
})();

/// Graceful shutdown - close the pool so in-flight queries can finish
process.on("SIGTERM", async () => {
  try { await pool?.end(); } finally { process.exit(0); }
});
process.on("SIGINT", async () => {
  try { await pool?.end(); } finally { process.exit(0); }
});
