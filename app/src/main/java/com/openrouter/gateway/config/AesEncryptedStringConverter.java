package com.openrouter.gateway.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts String columns
 * using AES/GCM/NoPadding with a random 12-byte IV per encryption.
 *
 * Storage format: Base64( IV[12 bytes] || Ciphertext + GCM tag[16 bytes] )
 *
 * The master key is loaded from the ENCRYPTION_MASTER_KEY environment variable
 * (32-byte hex string, e.g. from `openssl rand -hex 32`).
 *
 * Declared as @Component so Spring manages construction and injects the key via @Value.
 * autoApply = false — only applied where explicitly annotated with @Convert.
 */
@Component
@Converter(autoApply = false)
public class AesEncryptedStringConverter implements AttributeConverter<String, String> {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptedStringConverter.class);

    private static final String ALGORITHM      = "AES/GCM/NoPadding";
    private static final int    IV_LENGTH      = 12;   // 96-bit IV — recommended for GCM
    private static final int    TAG_LENGTH_BIT = 128;  // 128-bit authentication tag

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesEncryptedStringConverter(
            @Value("${encryption.master-key}") String masterKeyHex) {
        if (masterKeyHex == null || masterKeyHex.length() != 64) {
            throw new IllegalArgumentException(
                    "ENCRYPTION_MASTER_KEY must be a 64-character hex string (32 bytes). " +
                    "Generate with: openssl rand -hex 32");
        }
        byte[] keyBytes = hexToBytes(masterKeyHex);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        log.info("AesEncryptedStringConverter initialized successfully");
    }

    /**
     * Encrypts the entity attribute before persisting to the database.
     * Returns null if the value is null (no encryption of nulls).
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes());

            // Prepend IV to ciphertext, then Base64-encode the whole thing
            byte[] combined = new byte[IV_LENGTH + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt attribute", e);
        }
    }

    /**
     * Decrypts the database column value back to the entity attribute.
     * Returns null if the stored value is null.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv         = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(plaintext);
        } catch (Exception e) {
            log.error("Decryption failed — data may be corrupt or key changed: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt attribute", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
