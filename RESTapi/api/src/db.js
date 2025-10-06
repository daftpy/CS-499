import mysql from "mysql2/promise";

/// Create and return a shared connection pool
export async function makePool() {
    const pool = mysql.createPool({
        host: "mysql",
        port: Number(process.env.MYSQL_PORT || 3306),
        user: process.env.MYSQL_USER,
        password: process.env.MYSQL_PASSWORD,
        database: process.env.MYSQL_DATABASE,
        waitForConnections: true,
        connectionLimit: 10,
        timezone: "Z",
    });
    return pool;
}

/// Create tables if they don't exist
export async function migrate(pool) {
  // A simple table for Weight Tracker entries
    await pool.query(`
        CREATE TABLE IF NOT EXISTS weights (
            id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            user_sub     VARCHAR(255)     NOT NULL,        -- Keycloak subject (who owns the row)
            value        DECIMAL(6,2)     NOT NULL,        -- weight value
            recorded_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (id),
            INDEX idx_user_time (user_sub, recorded_at DESC)
        );
    `);

    // A table to track a users designated weight goal
    await pool.query(`
        CREATE TABLE IF NOT EXISTS weight_goals (
            user_sub     VARCHAR(255)     NOT NULL,        -- Keycloak subject (who owns the row)
            value        DECIMAL(6,2)     NOT NULL,        -- weight value
            recorded_at  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (user_sub)
        );
    `);
}

/// Insert one weight row and return its new id
export async function insertWeight(pool, userSub, value, recordedAt) {
  const [res] = await pool.execute(
    `INSERT INTO weights (user_sub, value, recorded_at)
     VALUES (?, ?, IFNULL(?, CURRENT_TIMESTAMP))`,
    [userSub, value, recordedAt ? toMySQLTimestamp(recordedAt) : null]
  );
  return res.insertId;
}

/// Convert a JS Date or ISO string into 'YYYY-MM-DD HH:mm:ss' in UTC
function toMySQLTimestamp(input) {
  const d = input instanceof Date ? input : new Date(input);
  if (Number.isNaN(d.getTime())) throw new Error("Invalid date");
  return d.toISOString().slice(0,19).replace('T',' ');
}

/// List latest weights for a user
export async function listWeights(pool, userSub, limit = 100, offset = 0) {
  // Clamp inputs to sane bounds
  const lim = Math.max(1, Math.min(500, Number(limit) || 100));
  const off = Math.max(0, Number(offset) || 0);

  // Return only the caller's rows
  const [rows] = await pool.query(
    `SELECT id, value, recorded_at
       FROM weights
      WHERE user_sub = ?
      ORDER BY recorded_at DESC
      LIMIT ${lim} OFFSET ${off}`,
    [userSub]
  );
  return rows;
}

/// Delete one weight row if it belongs to the user; returns affected row count
export async function deleteWeight(pool, id, userSub) {
    const [result] = await pool.execute(
        "DELETE FROM weights WHERE id = ? AND user_sub = ?",
        [id, userSub]
    );

    return result.affectedRows;
}

/// Insert or update the user's goal; returns affected row count
export async function upsertGoal(pool, userSub, weight, at) {
  // If client supplies a date, coerce, else DB sets NOW
  const ts = at ? toMySQLTimestamp(at) : null;

  // MySQL upsert: insert on first set, otherwise update both fields
  const [res] = await pool.execute(
  `INSERT INTO weight_goals (user_sub, value, recorded_at)
   VALUES (?, ?, IFNULL(?, CURRENT_TIMESTAMP)) AS new
   ON DUPLICATE KEY UPDATE
     value = new.value,
     recorded_at = IFNULL(new.recorded_at, CURRENT_TIMESTAMP)`,
  [userSub, weight, ts]
);
  return res.affectedRows; // 1 insert, 2 update (MySQL counts it as 2)
}

/// Fetch the user's goal (or null if none)
export async function getGoal(pool, userSub) {
  const [rows] = await pool.execute(
    `SELECT user_sub, value, recorded_at FROM weight_goals WHERE user_sub = ?`,
    [userSub]
  );
  return rows[0] || null;
}

/// Delete the user's goal and returns affected row count
export async function deleteGoal(pool, userSub) {
  const [res] = await pool.execute(
    `DELETE FROM weight_goals WHERE user_sub = ?`, [userSub]
  );
  return res.affectedRows;
}


/// Partially update a weight row and returns affected row count
export async function updateWeight(pool, id, userSub, value, recordedAt) {
  // Build dynamic SET clause based on provided fields
  const sets = [];
  const params = [];

  if (value !== undefined) {
    if (!Number.isFinite(Number(value))) throw new Error("Invalid value");
    sets.push("value = ?");
    params.push(Number(value));
  }

  if (recordedAt !== undefined) {
    // Convert to MySQL 'YYYY-MM-DD HH:mm:ss' or throw "Invalid date"
    const ts = toMySQLTimestamp(recordedAt);
    sets.push("recorded_at = ?");
    params.push(ts);
  }

  // Nothing to update -> no-op
  if (sets.length === 0) return 0;

  // Scope by id AND user_sub to enforce ownership
  params.push(id, userSub);

  const [res] = await pool.execute(
    `UPDATE weights SET ${sets.join(", ")} WHERE id = ? AND user_sub = ?`,
    params
  );
  return res.affectedRows;
}
