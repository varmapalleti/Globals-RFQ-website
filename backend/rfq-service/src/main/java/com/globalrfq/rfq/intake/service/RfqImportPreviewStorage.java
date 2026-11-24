package com.globalrfq.rfq.intake.service;

import com.globalrfq.rfq.common.error.RfqErrorCode;
import com.globalrfq.rfq.common.error.RfqException;
import com.globalrfq.rfq.config.RfqFileProperties;
import com.globalrfq.rfq.attachment.storage.RfqObjectStorage;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RfqImportPreviewStorage {
    private static final Logger log = LoggerFactory.getLogger(RfqImportPreviewStorage.class);
    private final RfqFileProperties properties;
    private final RfqObjectStorage objectStorage;

    public RfqImportPreviewStorage(RfqFileProperties properties, RfqObjectStorage objectStorage) {
        this.properties = properties;
        this.objectStorage = objectStorage;
    }

    public StoredPreviewFile store(MultipartFile file) {
        String key = UUID.randomUUID().toString();
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("rfq-import-", ".stage");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream input = file.getInputStream(); var output = Files.newOutputStream(temporaryFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    size += count;
                    if (size > properties.getMaxAttachmentSizeBytes()) {
                        throw new RfqException(RfqErrorCode.PAYLOAD_TOO_LARGE, "The import file exceeds the configured file size limit");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            objectStorage.put(RfqObjectStorage.ObjectKind.IMPORT, key, temporaryFile);
            return new StoredPreviewFile(key, size, HexFormat.of().formatHex(digest.digest()));
        } catch (RfqException exception) {
            delete(key);
            throw exception;
        } catch (Exception exception) {
            delete(key);
            throw new RfqException(RfqErrorCode.BAD_REQUEST, "The import preview source file could not be staged safely");
        } finally {
            deleteVerifiedCopy(temporaryFile);
        }
    }

    public Path verifiedPath(String key, String expectedSha256) {
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile("rfq-import-verified-", ".stage");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (InputStream input = objectStorage.open(RfqObjectStorage.ObjectKind.IMPORT, key);
                 var output = Files.newOutputStream(temporaryFile)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    size += count;
                    if (size > properties.getMaxAttachmentSizeBytes()) {
                        throw new RfqException(RfqErrorCode.PAYLOAD_TOO_LARGE, "The staged import file exceeds the configured file size limit");
                    }
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    expectedSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The staged source file failed its integrity check");
            }
            return temporaryFile;
        } catch (RfqException exception) {
            deleteVerifiedCopy(temporaryFile);
            throw exception;
        } catch (Exception exception) {
            deleteVerifiedCopy(temporaryFile);
            throw new RfqException(RfqErrorCode.INTERNAL_ERROR, "The staged import source file could not be verified");
        }
    }

    public void delete(String key) {
        try {
            objectStorage.delete(RfqObjectStorage.ObjectKind.IMPORT, key);
        } catch (RuntimeException exception) {
            log.warn("Could not delete staged RFQ import object; key={}", key, exception);
        }
    }

    public void deleteVerifiedCopy(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (Exception ignored) { }
    }

    public record StoredPreviewFile(String key, long sizeBytes, String sha256) { }
}
