package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.common.config.NotificationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class OutboxPayloadCipher {
    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public OutboxPayloadCipher(NotificationProperties properties) {
        String configured = properties.outboxEncryptionKey();
        if (configured == null || configured.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("OUTBOX_ENCRYPTION_KEY must contain at least 32 bytes");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(configured.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
            Arrays.fill(digest, (byte) 0);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize notification payload encryption", ex);
        }
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt notification payload", ex);
        }
    }

    public String decrypt(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            throw new IllegalStateException("Notification outbox payload is not encrypted with a supported key version");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length <= NONCE_LENGTH) throw new IllegalArgumentException("Invalid encrypted payload");
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(payload, NONCE_LENGTH, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt notification outbox payload", ex);
        }
    }
}
