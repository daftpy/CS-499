package com.zybooks.myapplication.auth;

import android.content.Context;
import android.content.SharedPreferences;

import net.openid.appauth.AuthState;

// Storage model for the applications auth state
public final class AuthStore {
    private static final String FILE = "auth_store";
    private static final String KEY  = "auth_state_json";
    private final SharedPreferences prefs;
    private final Context ctx;

    public AuthStore(Context ctx) throws Exception {
        this.ctx = ctx.getApplicationContext();

        // Ensure the AES key exists in Android Keystore
        Keys.ensure(this.ctx);

        // Plain SharedPreferences (the token is encrypted)
        this.prefs = this.ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // Serialize + encrypt + persist the AuthState.
    public void write(AuthState state) {
        try {
            String enc = Crypto.encrypt(state.jsonSerializeString());
            prefs.edit().putString(KEY, enc).apply();
        } catch (Exception ignore) {
            // Optional: log/report; leaving as no-op keeps auth UX resilient.
        }
    }

    // Load, decrypt, and deserialize the AuthState.
    public AuthState read() {
        String blob = prefs.getString(KEY, null);
        if (blob == null) return null;
        try {
            String json = Crypto.decrypt(blob);
            return AuthState.jsonDeserialize(json);
        } catch (Exception e) {
            // Includes JSON parse errors and decryption failures
            return null;
        }
    }

    // Removes the stored state (signs the user out locally).
    public void clear() { prefs.edit().remove(KEY).apply(); }
}
