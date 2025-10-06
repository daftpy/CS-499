package com.zybooks.myapplication.auth;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

// Small helper to encrypt/decrypt strings using an AES key stored in Android Keystore.
public final class Crypto {
    private static final String TFM = "AES/GCM/NoPadding";

    // Pull the symmetric AES key from AndroidKeyStore.
    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return (SecretKey) ks.getKey(Keys.ALIAS, null);
    }

    // Encrypts a UTF-8 string and returns iv:ciphertext
    public static String encrypt(String plain) throws Exception {
        Cipher c = Cipher.getInstance(TFM);
        c.init(Cipher.ENCRYPT_MODE, key()); // generates a fresh random IV internally
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        // Join as iv:ciphertext
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP);
    }

    // Decrypts an iv:ciphertext blob
    public static String decrypt(String blob) throws Exception {
        String[] parts = blob.split(":", 2); // split once: [iv, ciphertext]
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher c = Cipher.getInstance(TFM);
        c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        byte[] pt = c.doFinal(ct); // verifies tag; throws if tampered
        return new String(pt, StandardCharsets.UTF_8);
    }
}