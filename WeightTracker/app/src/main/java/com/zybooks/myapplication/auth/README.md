# Auth package

This is the package responsible for securely storing tokens from KeyCloak. It provides small utilities to persist and retrieve OAuth/OIDC session state
(`AuthState`) using the Android Keystore.

**Package:** `com.zybooks.myapplication.auth`

## What’s inside

- **`Keys`**
    - Creates (if missing) a **non-exportable AES-256 key** in the Android Keystore.
    - Uses AES/GCM, prefers **StrongBox** when available.
    - Idempotent: safe to call on every app start (`Keys.ensure(ctx)`).

- **`Crypto`**
    - Thin wrapper over AES/GCM for **encrypt/decrypt of UTF-8 strings**.
    - Returns/accepts a simple `base64(iv) : base64(ciphertext)` format.

- **`AuthStore`**
    - Wraps `SharedPreferences` to **persist `AuthState`**.
    - **Encrypts** JSON produced by `AuthState.jsonSerializeString()`.
    - API:
        - `write(AuthState state)`: serialize -> encrypt -> save
        - `AuthState read()`: load -> decrypt -> deserialize (returns `null` on error/absent)
        - `clear()`: remove the stored state

## Typical usage

**Save after exchanging the auth code for tokens:**

    AuthState state = new AuthState(resp, null);
    state.update(token, null);
    new AuthStore(this).write(state);

**Use later (refresh tokens as needed via AppAuth):**

    AuthStore store = new AuthStore(context);
    AuthState state = store.read();
    if (state != null && state.isAuthorized()) {
        AuthorizationService authService = new AuthorizationService(context);
        state.performActionWithFreshTokens(authService, (accessToken, idToken, ex) -> {
            if (ex == null) {
                // call the API with "Authorization: Bearer " + accessToken
                // optionally persist updated state (after refresh):
                try { store.write(state); } catch (Exception ignored) {}
            }
        });
    }

**Local sign-out:**

    new AuthStore(context).clear();

---

## Notes & caveats

- **Keys stay on the device.** The encrypted blob in `SharedPreferences` is only readable on the
  device that created it. After reinstall or on a new device, `read()` will return `null`
  — treat that as “signed out.”

- **How it’s protected.** Data is encrypted with AES/GCM using a key stored in Android Keystore
  (StrongBox when available). This protects the key from being copied off the device.

- **Writes are async.** `write()` uses `apply()` (non-blocking). If you must guarantee the write
  finished before continuing, use `commit()` instead.

- **Backups.** Because the key can’t be exported, auth data won’t survive device-to-device backups.
  Plan to re-authenticate after restore or reinstall.

- **Error handling.** `read()` returns `null` if decryption/parsing fails. Code should handle that
  as “no session.” Calling `Keys.ensure(context)` is safe on every app start.
