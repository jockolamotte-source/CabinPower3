package com.cabinpower.solar3;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

/** Credentials are encrypted with an on-device Android Keystore AES-GCM key. */
final class SecureStore {
    private static final String ALIAS = "cabin_power_3_credentials";
    private final SharedPreferences prefs;
    SecureStore(Context context) {
        prefs = context.getSharedPreferences("protected_credentials", Context.MODE_PRIVATE);
    }
    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return kg.generateKey();
    }
    synchronized void save(String user, String pass) throws Exception {
        JSONObject val = new JSONObject();
        val.put("username", user);
        val.put("password", pass);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(val.toString().getBytes(StandardCharsets.UTF_8));
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply();
    }
    synchronized String[] load() {
        String iv = prefs.getString("iv", null);
        String data = prefs.getString("data", null);
        if (iv == null || data == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            String s = new String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)),
                StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(s);
            return new String[]{obj.getString("username"), obj.getString("password")};
        } catch (Exception e) {
            // Corrupt/invalid key (e.g. after restoring a backup): require new login.
            clear();
            return null;
        }
    }
    synchronized void clear() { prefs.edit().clear().apply(); }
}
