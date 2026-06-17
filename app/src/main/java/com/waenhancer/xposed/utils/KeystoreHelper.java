package com.waenhancer.xposed.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Hardware-backed security layer for WaEnhancerX licensing system.
 * Manages an RSA key pair stored in the Android Hardware.
 */
public class KeystoreHelper {

    private static final String TAG = "KeystoreHelper";
    private static final String ALIAS = "WaEnhancerX_Device_Key";

    /**
     * Generates a new RSA key pair in the Android Hardware Keystore if it doesn't already exist.
     * Attempts to leverage StrongBox first, falling back to standard TEE (hardware-backed Keystore)
     * if StrongBox is unavailable or not supported by the device.
     */
    public static void generateRSAKeyPair() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(ALIAS)) {
                return;
            }

            try {
                // Try generating with StrongBox first (highest hardware security tier)
                generateKeyPairInternal(true);
            } catch (Exception e) {
                // Fallback to standard TEE-backed keystore
                generateKeyPairInternal(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate RSA key pair: ", e);
        }
    }

    /**
     * Internal helper to build and generate KeyPair with or without StrongBox.
     */
    private static void generateKeyPairInternal(boolean useStrongBox) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1);

        if (useStrongBox) {
            builder.setIsStrongBoxBacked(true);
        }

        kpg.initialize(builder.build());
        kpg.generateKeyPair();
    }

    /**
     * Retrieves the public key from the keystore using the alias and returns it as a Base64 encoded string.
     *
     * @return Base64 encoded public key string (NO_WRAP), or null if key does not exist or fails to load.
     */
    public static String getPublicKeyBase64() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(ALIAS)) {
                return null;
            }

            java.security.cert.Certificate cert = keyStore.getCertificate(ALIAS);
            if (cert == null) {
                return null;
            }

            PublicKey publicKey = cert.getPublicKey();
            if (publicKey == null) {
                return null;
            }

            byte[] publicKeyBytes = publicKey.getEncoded();
            return Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to retrieve public key: ", e);
            return null;
        }
    }

    /**
     * Signs the input challenge string using the private key associated with the alias.
     * Uses SHA256withRSA signature algorithm.
     *
     * @param challenge The challenge string to sign.
     * @return The signature as a Base64 encoded string (NO_WRAP), or null if signing fails.
     */
    public static String signData(String challenge) {
        if (challenge == null) {
            return null;
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (!keyStore.containsAlias(ALIAS)) {
                return null;
            }

            PrivateKey privateKey = (PrivateKey) keyStore.getKey(ALIAS, null);
            if (privateKey == null) {
                return null;
            }

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(challenge.getBytes(StandardCharsets.UTF_8));
            byte[] signedBytes = signature.sign();

            return Base64.encodeToString(signedBytes, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sign data: ", e);
            return null;
        }
    }
}
