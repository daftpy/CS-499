package com.zybooks.myapplication.auth;

import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;

// Creates (if missing) a non-exportable AES-256 key in the Android Keystore for encrypting
// local auth state. StrongBox is used when available.
public class Keys {
    // Keystore alias for the symmetric key.
    public static final String ALIAS = "auth_prefs_key";

    //  Key creation. Safe to call on every app start.
    public static void ensure(Context ctx) throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return;

        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);

        // Prefer StrongBox if present (falls back automatically if unavailable)
        if (ctx.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            try { b.setIsStrongBoxBacked(true); } catch (Throwable ignored) {}
        }

        kg.init(b.build());
        kg.generateKey();
    }
}
