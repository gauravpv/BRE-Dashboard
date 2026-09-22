package com.bredashboard.tester.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class AesCbcCrypto {

    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    private AesCbcCrypto() {
    }

    public static String encryptUtf8(String plainText, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
            );
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception ex) {
            throw new IllegalStateException("AES encryption failed: " + ex.getMessage(), ex);
        }
    }

    public static String decryptUtf8(String cipherTextBase64, String key, String iv) {
        try {
            String normalized = cipherTextBase64 == null ? "" : cipherTextBase64.trim();
            if (normalized.startsWith("\"") && normalized.endsWith("\"")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            byte[] cipherBytes = Base64.getDecoder().decode(normalized);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
            );
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("AES decryption failed: " + ex.getMessage(), ex);
        }
    }
}
