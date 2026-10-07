package com.example.postapi.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AesGcmEncryptionService {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;

    private final SecretKeySpec encryptionKey;
    private final String keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmEncryptionService(@Value("${app.crypto.key-base64:}") String keyBase64,
            @Value("${app.crypto.key-version:v1}") String keyVersion) {
        if (keyBase64.isBlank()) {
            throw new IllegalStateException("APP_AES_KEY_BASE64 must be configured before starting the application");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(keyBase64);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("APP_AES_KEY_BASE64 must contain valid Base64", exception);
        }
        if (key.length != 32) {
            throw new IllegalStateException("APP_AES_KEY_BASE64 must decode to exactly 32 bytes for AES-256");
        }
        if (keyVersion.isBlank()) {
            throw new IllegalStateException("APP_AES_KEY_VERSION must not be blank");
        }
        this.encryptionKey = new SecretKeySpec(key, "AES");
        this.keyVersion = keyVersion;
    }

    public EncryptedValue encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BYTES * 8, nonce));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            int ciphertextLength = ciphertextAndTag.length - TAG_BYTES;
            byte[] ciphertext = java.util.Arrays.copyOf(ciphertextAndTag, ciphertextLength);
            byte[] tag = java.util.Arrays.copyOfRange(ciphertextAndTag, ciphertextLength, ciphertextAndTag.length);
            Base64.Encoder encoder = Base64.getEncoder();
            return new EncryptedValue(keyVersion, encoder.encodeToString(nonce),
                    encoder.encodeToString(ciphertext), encoder.encodeToString(tag));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential encryption failed", exception);
        }
    }

    public String decrypt(EncryptedValue value) {
        if (value == null) {
            return null;
        }
        if (!keyVersion.equals(value.getKeyVersion())) {
            throw new IllegalStateException("No encryption key is configured for version " + value.getKeyVersion());
        }
        try {
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] nonce = decoder.decode(value.getNonce());
            byte[] ciphertext = decoder.decode(value.getCiphertext());
            byte[] tag = decoder.decode(value.getAuthenticationTag());
            if (nonce.length != NONCE_BYTES || tag.length != TAG_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted credential format");
            }
            ByteBuffer combined = ByteBuffer.allocate(ciphertext.length + tag.length).put(ciphertext).put(tag);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(TAG_BYTES * 8, nonce));
            return new String(cipher.doFinal(combined.array()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Credential decryption failed", exception);
        }
    }
}